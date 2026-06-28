"""
scrape_historical.py — Trivia 501
Historical Multi-League Scraper
=================================
Scrapes player statistics from FBref for the Top-5 European leagues across
all seasons since 2000-2001, writing directly to ``player_season_stints``.

Approach
--------
For each (league, season) pair:
  1. Scrape 'standard'    → appearances, goals, assists, penalties, cards, minutes
  2. Scrape 'goalkeeping' → updates GK rows with clean_sheets, goals_conceded

Both passes upsert into ``player_season_stints``. The goalkeeping pass only
touches rows where the player appeared in the GK table; outfield players are
unaffected.

Teams and players are created on first encounter using FBref's own names —
no translation map. Squad names are used verbatim so they remain consistent
across seasons within FBref's own naming scheme.

Real FBref player IDs (``Player ID_`` column, e.g. ``'774cf58b'``) are used
as the primary deduplication key, replacing the synthetic ``gen_`` IDs used
in earlier pipeline versions.

Resumability
------------
Progress is tracked in ``scrape_historical_checkpoint.json`` next to this
script. Re-running resumes from where it left off. Use ``--no-resume`` to
ignore the checkpoint and start fresh (useful after fixing a data bug).

Usage
-----
    python scrape_historical.py [options]

Options
-------
  --from-year YYYY     First season start year (default: 2000)
  --to-year   YYYY     Last  season start year (default: 2025, i.e. 2025-2026)
  --leagues   KEY,...  Comma-separated FBref league keys (default: all five)
  --no-resume          Ignore checkpoint; process every (league, season) fresh
  --dry-run            Parse and log but do not commit to the database

Rate limiting
-------------
FBref wait time is read from FBREF_WAIT_TIME env var (default: 7 s).
5 leagues × 26 seasons × 2 stat categories = 260 requests.
At 7 s/request plus ~10–20 s parse time, expect 3–5 hours total.
"""

from __future__ import annotations

import argparse
import json
import logging
import math
import os
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import pandas as pd
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from config import settings
from database.models_v6 import (
    Competition,
    Player,
    PlayerExternalId,
    PlayerSeasonStint,
    Season,
    ScrapeJob,
    ScrapeRunLog,
    Team,
    TeamExternalId,
)
from backfill_season_stints import (
    get_or_create_season,
    normalise_season_label,
)

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# League configuration
# ---------------------------------------------------------------------------
ALL_LEAGUES: List[Dict] = [
    {"fbref_key": "EPL",        "db_name": "Premier League", "country": "England"},
    {"fbref_key": "La Liga",    "db_name": "La Liga",        "country": "Spain"},
    {"fbref_key": "Serie A",    "db_name": "Serie A",        "country": "Italy"},
    {"fbref_key": "Bundesliga", "db_name": "Bundesliga",     "country": "Germany"},
    {"fbref_key": "Ligue 1",    "db_name": "Ligue 1",        "country": "France"},
]

CHECKPOINT_FILE = Path(__file__).parent / "scrape_historical_checkpoint.json"

# ---------------------------------------------------------------------------
# Utility helpers
# ---------------------------------------------------------------------------

def safe_int(val, default: int = 0) -> int:
    """Coerce a raw FBref cell value to int; return *default* on failure."""
    if val is None:
        return default
    s = str(val).replace(",", "").strip()
    if s.lower() in ("", "nan", "none", "-"):
        return default
    try:
        return int(float(s))
    except (ValueError, TypeError):
        return default


def normalize_name(name: str) -> str:
    """Lowercase + strip all non-alphanumeric characters for the normalized_name index."""
    return "".join(c for c in name.lower() if c.isalnum())


def parse_nationality(raw) -> Optional[str]:
    """
    Extract the 3-letter country code from FBref's Nation column.

    FBref returns values like ``'eng ENG'`` or ``'fr FRA'``.
    Returns the uppercase code (``'ENG'``, ``'FRA'``) or None.
    """
    if raw is None:
        return None
    s = str(raw).strip()
    if s.lower() in ("", "nan", "none"):
        return None
    parts = s.split()
    return parts[-1].upper() if parts else None


def flatten_columns(df: pd.DataFrame) -> pd.DataFrame:
    """Collapse FBref's MultiIndex columns to ``'Group_Stat'`` strings."""
    if isinstance(df.columns, pd.MultiIndex):
        df.columns = [
            f"{g}_{s}" if not str(g).startswith("Unnamed") else s
            for g, s in df.columns.values
        ]
    return df


def strip_header_rows(df: pd.DataFrame) -> pd.DataFrame:
    """Remove repeated FBref mid-table header rows (where Player == 'Player')."""
    if "Player" in df.columns:
        df = df[df["Player"] != "Player"].reset_index(drop=True)
    return df


def col(row: pd.Series, *candidates, default=0) -> int:
    """
    Return safe_int of the first candidate column name that exists in *row*.
    Accepts positional args so callers can list fallback column names.
    """
    for name in candidates:
        if name in row.index:
            return safe_int(row[name], default)
    return default


def player_fbref_id(row: pd.Series) -> Optional[str]:
    """Extract the real FBref player ID from the scraped row."""
    for candidate in ("Player ID_", "Player ID", "player_id"):
        if candidate in row.index:
            val = str(row[candidate]).strip()
            if val and val.lower() not in ("nan", "none", ""):
                return val
    return None

# ---------------------------------------------------------------------------
# Database upsert helpers
# ---------------------------------------------------------------------------

def upsert_team(session: Session, fbref_squad_name: str, country: str) -> Team:
    """
    Find or create a Team row using FBref's squad name verbatim.

    Matches on normalized_name (not name) so that teams with a stale/broken
    display name get their name column fixed instead of creating a duplicate.
    """
    norm = normalize_name(fbref_squad_name)
    team = session.query(Team).filter_by(normalized_name=norm).first()

    if team is None:
        team = Team(
            name=fbref_squad_name,
            normalized_name=norm,
            team_type="club",
            country=country,
        )
        session.add(team)
        session.flush()
        log.debug("    Created team: %s", fbref_squad_name)
    elif team.name != fbref_squad_name:
        team.name = fbref_squad_name
        session.flush()
        log.debug("    Fixed team name: %s → %s", team.name, fbref_squad_name)

    # Upsert the FBref external-ID record (team has no fbref_id column after V9
    # but team_external_ids is the right place regardless).
    _ensure_team_external_id(session, team, fbref_squad_name)
    return team


def upsert_player(
    session: Session,
    fbref_id: str,
    name: str,
    nationality: Optional[str],
) -> Player:
    """
    Find or create a Player row.

    Post-V9: the ``players`` table no longer has a ``fbref_id`` column.
    Lookup uses ``player_external_ids`` (source='fbref') for real IDs, and
    falls back to ``normalized_name`` for synthetic ``gen_`` IDs (edge case:
    FBref page missing the ID column for very old seasons).
    """
    player = None

    # Primary lookup via player_external_ids (real FBref IDs only)
    if fbref_id and not fbref_id.startswith("gen_"):
        ext = session.query(PlayerExternalId).filter_by(
            source="fbref", external_id=fbref_id
        ).first()
        if ext:
            player = session.query(Player).filter_by(id=ext.player_id).first()

    # Fallback: normalized name (synthetic gen_ IDs or missing ID column)
    if player is None:
        norm = normalize_name(name)
        player = session.query(Player).filter_by(normalized_name=norm).first()

    if player is None:
        player = Player(
            name=name,
            normalized_name=normalize_name(name),
            nationality=nationality,
            last_scraped_at=datetime.utcnow(),
        )
        session.add(player)
        session.flush()
    else:
        # Keep display name and nationality fresh from the latest scrape.
        player.name = name
        if nationality:
            player.nationality = nationality
        player.last_scraped_at = datetime.utcnow()

    _ensure_player_external_id(session, player, fbref_id)
    return player


def _ensure_player_external_id(session: Session, player: Player, fbref_id: str) -> None:
    """Upsert a player_external_ids row for a real (non-synthetic) FBref ID."""
    if not fbref_id or fbref_id.startswith("gen_"):
        return
    exists = session.query(PlayerExternalId).filter_by(
        source="fbref", external_id=fbref_id
    ).first()
    if not exists:
        session.add(PlayerExternalId(
            player_id=player.id,
            source="fbref",
            external_id=fbref_id,
            confidence=100,
        ))


def _ensure_team_external_id(session: Session, team: Team, fbref_name: str) -> None:
    exists = session.query(TeamExternalId).filter_by(
        source="fbref", external_id=fbref_name
    ).first()
    if not exists:
        session.add(TeamExternalId(
            team_id=team.id,
            source="fbref",
            external_id=fbref_name,
            confidence=100,
        ))


def upsert_stint(
    session: Session,
    player: Player,
    season: Season,
    team: Team,
    competition: Competition,
    *,
    appearances: int,
    starts: int,
    minutes: int,
    goals: int,
    penalty_goals: int,
    penalty_attempts: int,
    assists: int,
    yellow_cards: int,
    red_cards: int,
    # Goalkeeper fields — only supplied by the GK pass
    clean_sheets: Optional[int] = None,
    goals_conceded: Optional[int] = None,
    is_goalkeeper: Optional[bool] = None,
) -> Tuple[PlayerSeasonStint, bool]:
    """
    Upsert a player_season_stints row.

    Returns ``(stint, created)``.  When called from the standard pass,
    goalkeeper fields are left at their existing/default value.  When called
    from the goalkeeping pass, only GK fields are updated.
    """
    now = datetime.utcnow()

    existing = (
        session.query(PlayerSeasonStint)
        .filter_by(
            player_id=player.id,
            season_id=season.id,
            team_id=team.id,
            competition_id=competition.id,
        )
        .first()
    )

    if existing is None:
        stint = PlayerSeasonStint(
            player_id=player.id,
            season_id=season.id,
            team_id=team.id,
            competition_id=competition.id,
            appearances=appearances,
            starts=starts,
            sub_appearances=max(0, appearances - starts),
            minutes=minutes,
            goals=goals,
            penalty_goals=penalty_goals,
            penalty_attempts=penalty_attempts,
            assists=assists,
            yellow_cards=yellow_cards,
            red_cards=red_cards,
            clean_sheets=clean_sheets if clean_sheets is not None else 0,
            goals_conceded=goals_conceded if goals_conceded is not None else 0,
            is_goalkeeper=is_goalkeeper if is_goalkeeper is not None else False,
            source="fbref",
            source_scraped_at=now,
        )
        session.add(stint)
        return stint, True

    # Update outfield stats unconditionally (standard pass wins).
    existing.appearances    = appearances
    existing.starts         = starts
    existing.sub_appearances = max(0, appearances - starts)
    existing.minutes        = minutes
    existing.goals          = goals
    existing.penalty_goals  = penalty_goals
    existing.penalty_attempts = penalty_attempts
    existing.assists        = assists
    existing.yellow_cards   = yellow_cards
    existing.red_cards      = red_cards
    existing.source_scraped_at = now
    existing.updated_at     = now

    # GK fields: only update when the caller explicitly supplies them.
    if clean_sheets is not None:
        existing.clean_sheets = clean_sheets
    if goals_conceded is not None:
        existing.goals_conceded = goals_conceded
    if is_goalkeeper is not None:
        existing.is_goalkeeper = is_goalkeeper

    return existing, False

# ---------------------------------------------------------------------------
# DataFrame processing passes
# ---------------------------------------------------------------------------

def process_standard(
    session: Session,
    player_df: pd.DataFrame,
    season: Season,
    competition: Competition,
    country: str,
    job: ScrapeJob,
) -> Tuple[int, int, int]:
    """
    Process the standard stats DataFrame for one (league, season).

    Returns ``(created, updated, failed)`` counts.
    """
    created = updated = failed = 0

    for _, row in player_df.iterrows():
        name = str(row.get("Player", "")).strip()
        if not name or name.lower() == "player":
            continue

        squad = str(row.get("Squad", "")).strip()
        if not squad or squad.lower() in ("nan", ""):
            continue

        fbref_id = player_fbref_id(row)
        if not fbref_id:
            fbref_id = f"gen_{normalize_name(name)}"

        try:
            team = upsert_team(session, squad, country)
            player = upsert_player(
                session,
                fbref_id=fbref_id,
                name=name,
                nationality=parse_nationality(row.get("Nation")),
            )

            apps   = col(row, "Playing Time_MP",     "Playing_Time_MP")
            starts = col(row, "Playing Time_Starts",  "Playing_Time_Starts")
            mins   = col(row, "Playing Time_Min",     "Playing_Time_Min")
            goals  = col(row, "Performance_Gls")
            pk     = col(row, "Performance_PK")
            pkatt  = col(row, "Performance_PKatt")
            ast    = col(row, "Performance_Ast")
            yc     = col(row, "Performance_CrdY")
            rc     = col(row, "Performance_CrdR")

            if apps == 0:
                continue    # skip players with no appearances

            _, was_created = upsert_stint(
                session,
                player=player,
                season=season,
                team=team,
                competition=competition,
                appearances=apps,
                starts=starts,
                minutes=mins,
                goals=goals,
                penalty_goals=pk,
                penalty_attempts=pkatt,
                assists=ast,
                yellow_cards=yc,
                red_cards=rc,
            )

            if was_created:
                created += 1
            else:
                updated += 1

        except Exception as exc:
            log.warning("    [standard] Error for %r (%s): %s", name, squad, exc)
            session.add(ScrapeRunLog(
                job_id=job.id,
                level="ERROR",
                message=f"standard pass error for {name!r}: {exc}",
                context={"player": name, "squad": squad,
                         "season": season.label, "competition": competition.name},
            ))
            failed += 1

    return created, updated, failed


def process_goalkeeping(
    session: Session,
    gk_df: pd.DataFrame,
    season: Season,
    competition: Competition,
    country: str,
    job: ScrapeJob,
) -> Tuple[int, int, int]:
    """
    Second pass: update GK rows with clean sheets and goals conceded.

    Rows are matched by FBref player ID + (season, team, competition).
    Returns ``(updated, created_new, failed)`` — 'created_new' should be ~0.
    """
    updated = created_new = failed = 0

    for _, row in gk_df.iterrows():
        name = str(row.get("Player", "")).strip()
        if not name or name.lower() == "player":
            continue

        squad = str(row.get("Squad", "")).strip()
        if not squad or squad.lower() in ("nan", ""):
            continue

        fbref_id = player_fbref_id(row)
        if not fbref_id:
            fbref_id = f"gen_{normalize_name(name)}"

        try:
            team   = upsert_team(session, squad, country)
            player = upsert_player(
                session,
                fbref_id=fbref_id,
                name=name,
                nationality=parse_nationality(row.get("Nation")),
            )

            apps   = col(row, "Playing Time_MP",    "Playing_Time_MP")
            starts = col(row, "Playing Time_Starts", "Playing_Time_Starts")
            mins   = col(row, "Playing Time_Min",   "Playing_Time_Min")
            cs     = col(row, "Performance_CS")
            ga     = col(row, "Performance_GA")

            if apps == 0:
                continue

            _, was_created = upsert_stint(
                session,
                player=player,
                season=season,
                team=team,
                competition=competition,
                # Carry through playing-time stats in case this GK was
                # absent from the standard table (rare but possible).
                appearances=apps,
                starts=starts,
                minutes=mins,
                goals=0,
                penalty_goals=0,
                penalty_attempts=0,
                assists=0,
                yellow_cards=0,
                red_cards=0,
                # GK-specific fields
                clean_sheets=cs,
                goals_conceded=ga,
                is_goalkeeper=True,
            )

            if was_created:
                created_new += 1
            else:
                updated += 1

        except Exception as exc:
            log.warning("    [goalkeeping] Error for %r (%s): %s", name, squad, exc)
            session.add(ScrapeRunLog(
                job_id=job.id,
                level="ERROR",
                message=f"goalkeeping pass error for {name!r}: {exc}",
                context={"player": name, "squad": squad,
                         "season": season.label, "competition": competition.name},
            ))
            failed += 1

    return updated, created_new, failed

# ---------------------------------------------------------------------------
# Chrome + FBref setup
# ---------------------------------------------------------------------------

class ChromeConnectionError(Exception):
    """
    Raised when the ChromeDriver HTTP connection dies mid-run.

    Distinct from data errors (bad player rows, missing columns) so that
    the caller can restart Chrome and retry the current season rather than
    just logging and moving on.
    """


def build_fbref_client():
    """
    Launch undetected Chrome and return a patched FBref instance that routes
    all HTTP requests through the browser to bypass Cloudflare.

    Page-load timeout is set to 90 s so Chrome abandons hung pages well
    before Selenium's 120 s HTTP-connection timeout fires.  This converts
    a 2-minute hang into a clean Selenium TimeoutException that the caller
    can handle immediately.
    """
    import undetected_chromedriver as uc
    from ScraperFC.fbref import FBref

    log.info("Launching Chrome (undetected) to bypass Cloudflare…")
    driver = uc.Chrome(headless=False, version_main=148)
    driver.set_page_load_timeout(90)   # fail fast; don't let pages hang forever
    fb     = FBref(wait_time=settings.fbref_wait_time)

    def _wait_for_cloudflare(url: str) -> None:
        try:
            driver.get(url)
        except Exception:
            # Page-load timeout — page source may still be partially available;
            # let chrome_get decide whether it's usable.
            pass
        for _ in range(30):
            if "Just a moment" not in driver.title:
                break
            time.sleep(1)
        time.sleep(fb.wait_time)

    def chrome_get(url: str):
        class _R:
            status_code = 200
        _wait_for_cloudflare(url)
        _R.content = driver.page_source.encode("utf-8")
        return _R

    fb._get          = chrome_get
    fb._driver_init  = lambda: None
    fb.driver        = driver
    fb._driver_get   = _wait_for_cloudflare
    fb._driver_close = lambda: None

    return driver, fb

# ---------------------------------------------------------------------------
# Checkpoint helpers
# ---------------------------------------------------------------------------

def load_checkpoint() -> Dict:
    if CHECKPOINT_FILE.exists():
        return json.loads(CHECKPOINT_FILE.read_text())
    return {}


def save_checkpoint(cp: Dict) -> None:
    CHECKPOINT_FILE.write_text(json.dumps(cp, indent=2))


def checkpoint_key(fbref_key: str, year_str: str) -> str:
    return f"{fbref_key}_{year_str}"


def is_done(cp: Dict, fbref_key: str, year_str: str, category: str) -> bool:
    return cp.get(checkpoint_key(fbref_key, year_str), {}).get(category, False)


def mark_done(cp: Dict, fbref_key: str, year_str: str, category: str) -> None:
    key = checkpoint_key(fbref_key, year_str)
    cp.setdefault(key, {})[category] = True

# ---------------------------------------------------------------------------
# Per-season scrape
# ---------------------------------------------------------------------------

def scrape_league_season(
    fb,
    session: Session,
    league: Dict,
    year_str: str,
    competition: Competition,
    checkpoint: Dict,
    dry_run: bool,
) -> bool:
    """
    Scrape standard + goalkeeping stats for one (league, season) pair.

    Returns True if the season was fully processed (both categories done).
    Returns False on a non-recoverable error.
    Seasons not available on FBref are skipped silently (return True so they
    are not retried).
    """
    fbref_key = league["fbref_key"]
    country   = league["country"]
    label     = normalise_season_label(year_str)

    season = get_or_create_season(session, year_str)

    job = ScrapeJob(
        job_type="historical_scrape",
        season=label,
        competition_id=competition.id,
        status="running",
        started_at=datetime.utcnow(),
    )
    session.add(job)
    session.flush()

    all_ok = True

    for category in ("standard", "goalkeeping"):
        if is_done(checkpoint, fbref_key, year_str, category):
            log.info("  [%s] %s already done — skipping", category, label)
            continue

        log.info("  Scraping [%s] %s %s…", category, fbref_key, label)

        try:
            raw = fb.scrape_stats(year_str, fbref_key, category)
        except Exception as exc:
            exc_name = type(exc).__name__
            msg      = str(exc)

            if "not a valid year" in msg or "InvalidYear" in exc_name:
                # FBref doesn't have this league/season — not an error.
                log.info("  %s %s not available on FBref — skipping season.", fbref_key, year_str)
                mark_done(checkpoint, fbref_key, year_str, "standard")
                mark_done(checkpoint, fbref_key, year_str, "goalkeeping")
                save_checkpoint(checkpoint)
                job.status = "skipped"
                job.completed_at = datetime.utcnow()
                if not dry_run:
                    session.commit()
                return True

            # Chrome connection / Selenium errors — the browser has died.
            # Re-raise so run() can restart Chrome and retry this season.
            _chrome_signals = (
                "ReadTimeoutError", "HTTPConnectionPool", "MaxRetryError",
                "WebDriverException", "TimeoutException", "SessionNotCreated",
                "InvalidSessionId",
            )
            if any(sig in msg or sig in exc_name for sig in _chrome_signals):
                raise ChromeConnectionError(
                    f"{fbref_key} {year_str} [{category}]: {exc}"
                ) from exc

            # Any other error is a data/FBref issue — log and continue.
            log.error("  Error scraping %s %s [%s]: %s", fbref_key, year_str, category, exc)
            session.add(ScrapeRunLog(
                job_id=job.id, level="ERROR",
                message=f"scrape_stats failed: {exc}",
                context={"league": fbref_key, "season": year_str, "category": category},
            ))
            all_ok = False
            continue

        # Unpack the (squad, opp, player) tuple
        if isinstance(raw, (tuple, list)) and len(raw) == 3:
            _, _, player_df = raw
        else:
            player_df = raw

        if player_df is None or not isinstance(player_df, pd.DataFrame) or player_df.empty:
            log.warning("  Empty player data for %s %s [%s]", fbref_key, year_str, category)
            mark_done(checkpoint, fbref_key, year_str, category)
            save_checkpoint(checkpoint)
            continue

        player_df = flatten_columns(player_df)
        player_df = strip_header_rows(player_df)

        if category == "standard":
            c, u, f = process_standard(session, player_df, season, competition, country, job)
            log.info("    standard: %d created, %d updated, %d failed", c, u, f)
        else:
            u, c_new, f = process_goalkeeping(session, player_df, season, competition, country, job)
            log.info("    goalkeeping: %d updated, %d new, %d failed", u, c_new, f)

        if not dry_run:
            session.commit()

        mark_done(checkpoint, fbref_key, year_str, category)
        save_checkpoint(checkpoint)

    job.status = "success" if all_ok else "partial"
    job.completed_at = datetime.utcnow()
    if not dry_run:
        session.commit()

    return all_ok

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def run(
    from_year: int,
    to_year: int,
    league_keys: List[str],
    resume: bool,
    dry_run: bool,
) -> None:
    leagues = [l for l in ALL_LEAGUES if l["fbref_key"] in league_keys]
    if not leagues:
        log.error("No matching leagues found for: %s", league_keys)
        sys.exit(1)

    # Work backwards — most recent seasons first so the DB is useful
    # as quickly as possible and interrupted runs leave us with the
    # freshest data rather than only old historical records.
    seasons = [f"{y}-{y + 1}" for y in range(to_year, from_year - 1, -1)]
    total   = len(leagues) * len(seasons) * 2  # ×2 for standard+goalkeeping

    log.info("=== Historical Scrape ===")
    log.info("Leagues : %s", [l["fbref_key"] for l in leagues])
    log.info("Seasons : %s → %s  (%d seasons, newest first)", seasons[0], seasons[-1], len(seasons))
    log.info("Requests: ~%d  (est. %.1f hours at %ds/req)",
             total, total * (settings.fbref_wait_time + 15) / 3600, settings.fbref_wait_time)
    if dry_run:
        log.warning("DRY-RUN: no changes will be committed.")

    checkpoint = load_checkpoint() if resume else {}

    engine  = create_engine(settings.database_url)
    Session = sessionmaker(bind=engine)
    session = Session()

    driver, fb = build_fbref_client()

    MAX_CHROME_RETRIES = 3

    try:
        for league in leagues:
            fbref_key = league["fbref_key"]
            db_name   = league["db_name"]

            competition = session.query(Competition).filter_by(name=db_name).first()
            if competition is None:
                log.error("Competition %r not found in DB — skipping league.", db_name)
                continue

            log.info("\n%s  (%s)", "=" * 60, fbref_key)

            done_count = sum(
                1 for yr in seasons
                if is_done(checkpoint, fbref_key, yr, "standard")
                and is_done(checkpoint, fbref_key, yr, "goalkeeping")
            )
            log.info("Progress: %d/%d seasons already complete", done_count, len(seasons))

            for year_str in seasons:
                already_done = (
                    is_done(checkpoint, fbref_key, year_str, "standard")
                    and is_done(checkpoint, fbref_key, year_str, "goalkeeping")
                )
                if already_done:
                    continue

                log.info("\n--- %s  %s ---", fbref_key, year_str)

                for attempt in range(1, MAX_CHROME_RETRIES + 1):
                    try:
                        scrape_league_season(
                            fb=fb,
                            session=session,
                            league=league,
                            year_str=year_str,
                            competition=competition,
                            checkpoint=checkpoint,
                            dry_run=dry_run,
                        )
                        break  # success — move to next season

                    except ChromeConnectionError as exc:
                        log.warning(
                            "  Chrome connection died (attempt %d/%d): %s",
                            attempt, MAX_CHROME_RETRIES, exc,
                        )
                        # Cleanly quit the dead browser.
                        try:
                            driver.quit()
                        except Exception:
                            pass

                        if attempt < MAX_CHROME_RETRIES:
                            wait = 15 * attempt   # 15 s, 30 s back-off
                            log.info("  Restarting Chrome in %d s…", wait)
                            time.sleep(wait)
                            driver, fb = build_fbref_client()
                            # Rollback any partial session state for this season.
                            session.rollback()
                        else:
                            log.error(
                                "  Chrome failed %d times for %s %s — "
                                "season left in checkpoint as incomplete; "
                                "will retry on next run.",
                                MAX_CHROME_RETRIES, fbref_key, year_str,
                            )
                            # Rebuild Chrome so subsequent seasons can proceed.
                            log.info("  Rebuilding Chrome to continue with remaining seasons…")
                            time.sleep(30)
                            driver, fb = build_fbref_client()
                            session.rollback()

    finally:
        try:
            driver.quit()
        except Exception:
            pass
        log.info("\nBrowser closed.")

    # Summary
    completed = sum(
        1 for key, val in checkpoint.items()
        if val.get("standard") and val.get("goalkeeping")
    )
    log.info("\n=== Scrape complete ===")
    log.info("Season/league pairs completed : %d", completed)
    log.info("Checkpoint saved to           : %s", CHECKPOINT_FILE)
    log.info("\nNext step: python verify_parity.py")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Trivia 501 — Historical Multi-League Scraper")
    parser.add_argument("--from-year", type=int, default=2000,
                        help="First season start year (default: 2000)")
    parser.add_argument("--to-year",   type=int, default=2025,
                        help="Last season start year (default: 2025, i.e. 2025-2026)")
    parser.add_argument("--leagues",   type=str,
                        default=",".join(l["fbref_key"] for l in ALL_LEAGUES),
                        help="Comma-separated FBref league keys")
    parser.add_argument("--no-resume", action="store_true",
                        help="Ignore checkpoint and reprocess everything")
    parser.add_argument("--dry-run",   action="store_true",
                        help="Parse and log but do not commit to the database")
    args = parser.parse_args()

    run(
        from_year=args.from_year,
        to_year=args.to_year,
        league_keys=[k.strip() for k in args.leagues.split(",")],
        resume=not args.no_resume,
        dry_run=args.dry_run,
    )
