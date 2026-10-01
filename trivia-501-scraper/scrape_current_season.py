"""
Scrape Current Season Statistics
---------------------------------
Fetches player statistics from FBref for all Top-5 European leagues and
writes directly to ``player_season_stints`` (post-V9 schema).

Two passes are made per league, mirroring the historical scraper:
  1. Standard stats  — appearances, goals, assists, penalty stats, cards
  2. Goalkeeping     — clean sheets, goals conceded (updates GK rows only)

Run this script after each matchday (or weekly) to keep current-season
data fresh.  Answers are re-materialized automatically by the backend's AnswerRematerializationScheduler (Mondays 05:17 UTC).

    POST /api/admin/templates/generate   ← only needed at start of a new season

Post-V9 schema notes
--------------------
* ``players.fbref_id`` and ``players.career_stats`` no longer exist.
* Player identity is keyed through ``player_external_ids`` (source='fbref').
* All season stats live in ``player_season_stints``.

Usage
-----
    python scrape_current_season.py [--season 2026-2027] [--leagues EPL,La Liga]

Options
-------
  --season YYYY-YYYY   Season to scrape (default: settings.current_season)
  --leagues KEY,...    Comma-separated FBref league keys (default: all five)
  --dry-run            Parse and log but do not commit to the database
"""

import argparse
import logging
import sys
import os
from datetime import datetime

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from config import settings
from database.models_v6 import Competition, ScrapeJob, ScrapeRunLog
from backfill_season_stints import normalise_season_label, get_or_create_season

# Re-use all processing logic from the historical scraper.
# This guarantees identical upsert behaviour: real FBref IDs, None-sentinel
# GK fields, penalty stats, and correct country per league.
from scrape_historical import (
    ALL_LEAGUES,
    build_fbref_client,
    process_standard,
    process_goalkeeping,
)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def run_scrape(season_str: str, league_keys: list[str], dry_run: bool = False) -> None:
    """
    Scrape standard + goalkeeping stats for the given season across all
    requested leagues.

    Args:
        season_str:  FBref season string, e.g. "2025-2026"
        league_keys: list of FBref league keys to scrape, e.g. ["EPL", "La Liga"]
        dry_run:     if True, parse and log but do not commit to the database
    """
    leagues = [l for l in ALL_LEAGUES if l["fbref_key"] in league_keys]
    if not leagues:
        log.error("No matching leagues for keys: %s", league_keys)
        sys.exit(1)

    season_label = normalise_season_label(season_str)
    log.info("=== Current Season Scrape: %s ===", season_label)
    log.info("Leagues : %s", [l["fbref_key"] for l in leagues])
    if dry_run:
        log.warning("DRY-RUN: no changes will be committed.")

    engine  = create_engine(settings.database_url)
    Session = sessionmaker(bind=engine)
    session = Session()

    season_row = get_or_create_season(session, season_str)
    if not dry_run:
        session.commit()

    driver, fb = build_fbref_client()

    total_created = total_updated = total_failed = 0

    try:
        for league in leagues:
            fbref_key = league["fbref_key"]
            db_name   = league["db_name"]
            country   = league["country"]

            log.info("\n%s  (%s — %s)", "=" * 50, fbref_key, season_label)

            competition = session.query(Competition).filter_by(name=db_name).first()
            if competition is None:
                log.error("Competition %r not found in DB — skipping.", db_name)
                session.add(ScrapeRunLog(
                    job_id=None, level="ERROR",
                    message=f"Competition not found: {db_name}",
                    context={"league": fbref_key, "season": season_label},
                ))
                continue

            league_created = league_updated = league_failed = 0

            for category in ("standard", "goalkeeping"):
                log.info("  [%s] Fetching %s…", category, fbref_key)

                # One ScrapeJob row per (league, category) for auditability.
                job = ScrapeJob(
                    job_type="weekly_update",
                    season=season_label,
                    competition_id=competition.id,
                    status="running",
                    started_at=datetime.utcnow(),
                )
                session.add(job)
                session.flush()

                try:
                    raw = fb.scrape_stats(season_str, fbref_key, category)
                except Exception as exc:
                    log.error("  Error fetching %s [%s]: %s", fbref_key, category, exc)
                    session.add(ScrapeRunLog(
                        job_id=job.id, level="ERROR",
                        message=f"scrape_stats failed: {exc}",
                        context={"league": fbref_key, "season": season_label,
                                 "category": category},
                    ))
                    job.status = "failed"
                    job.completed_at = datetime.utcnow()
                    if not dry_run:
                        session.commit()
                    league_failed += 1
                    continue

                # Unpack (squad_df, opp_df, player_df) tuple from ScraperFC
                if isinstance(raw, (tuple, list)) and len(raw) == 3:
                    _, _, player_df = raw
                else:
                    player_df = raw

                if player_df is None or player_df.empty:
                    log.warning("  Empty data for %s [%s] — skipping.", fbref_key, category)
                    job.status = "skipped"
                    job.completed_at = datetime.utcnow()
                    if not dry_run:
                        session.commit()
                    continue

                # Delegate to the shared processing functions from the
                # historical scraper.  These handle: real FBref player IDs,
                # penalty stats, None-sentinel GK fields, country-aware
                # team upsert, and player_external_ids maintenance.
                if category == "standard":
                    c, u, f = process_standard(
                        session, player_df, season_row, competition, country, job
                    )
                    log.info("    standard: %d created, %d updated, %d failed", c, u, f)
                    league_created += c
                    league_updated += u
                    league_failed  += f
                else:
                    u, c_new, f = process_goalkeeping(
                        session, player_df, season_row, competition, country, job
                    )
                    log.info("    goalkeeping: %d updated, %d new GKs, %d failed",
                             u, c_new, f)
                    league_updated += u + c_new
                    league_failed  += f

                job.status = "success" if f == 0 else "partial"
                job.players_scraped = (c + u) if category == "standard" else (u + c_new)
                job.players_failed  = f
                job.completed_at    = datetime.utcnow()
                if not dry_run:
                    session.commit()

            log.info("  %s total: %d created, %d updated, %d failed",
                     fbref_key, league_created, league_updated, league_failed)
            total_created += league_created
            total_updated += league_updated
            total_failed  += league_failed

    finally:
        try:
            driver.quit()
        except Exception:
            pass
        log.info("\nBrowser closed.")

    log.info("\n=== Scrape complete ===")
    log.info("Season : %s", season_label)
    log.info("Total  : %d created, %d updated, %d failed",
             total_created, total_updated, total_failed)
    log.info("\nNext steps:")
    log.info("  POST /api/admin/templates/generate"
             "              ← create drafts for new (team, season) combos")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Trivia 501 — Current Season Scraper"
    )
    parser.add_argument(
        "--season",
        type=str,
        default=settings.current_season,
        help=f"Season to scrape, e.g. '2026-2027' (default: {settings.current_season})",
    )
    parser.add_argument(
        "--leagues",
        type=str,
        default=",".join(l["fbref_key"] for l in ALL_LEAGUES),
        help="Comma-separated FBref league keys (default: all five)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Parse and log but do not commit to the database",
    )
    args = parser.parse_args()

    run_scrape(
        season_str=args.season,
        league_keys=[k.strip() for k in args.leagues.split(",")],
        dry_run=args.dry_run,
    )
