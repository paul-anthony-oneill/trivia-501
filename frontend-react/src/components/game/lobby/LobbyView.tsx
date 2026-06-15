"use client";

import { useState, useCallback, useEffect, useRef } from "react";
import dynamic from "next/dynamic";
const LoginButton = dynamic(() => import("@/components/auth/LoginButton"), { ssr: false });
import HowToPlayPanel from "../HowToPlayPanel";
import ThemeToggle from "@/components/ui/ThemeToggle";
import { useAuth } from "@/context/AuthContext";
import { fetchClubs, type FootballClub, type FootballFilter } from "@/lib/api/footballApi";
import type { CategoryChallenge } from "@/hooks/useDailyChallenge";

// ─── Static data ──────────────────────────────────────────────────────────────

type TargetScore = 501 | 301 | 101 | "random";
const TARGET_OPTIONS: TargetScore[] = [501, 301, 101, "random"];

const LEAGUES = [
  { id: "premier-league",   name: "Premier League" },
  { id: "la-liga",          name: "La Liga" },
  { id: "bundesliga",       name: "Bundesliga" },
  { id: "serie-a",          name: "Serie A" },
  { id: "champions-league", name: "Champions League" },
] as const;
type League = (typeof LEAGUES)[number];

const STAT_TYPES = [
  { id: "goals",                    name: "Goals",                          sub: "Goals scored since 2000" },
  { id: "assists",                  name: "Assists",                        sub: "Goal assists since 2000" },
  { id: "appearances",              name: "Appearances",                    sub: "Games played since 2000" },
  { id: "goals_assists",            name: "Goals + Assists",                sub: "Combined total" },
  { id: "goals_appearances",        name: "Goals + Appearances",            sub: "Combined total" },
  { id: "assists_appearances",      name: "Assists + Appearances",          sub: "Combined total" },
  { id: "goals_assists_appearances", name: "Goals + Assists + Appearances", sub: "All three combined" },
] as const;

const OTHER_CATEGORIES = [
  { id: "film",      name: "Film",      description: "Worldwide box office hits" },
  { id: "geography", name: "Geography", description: "Populations, capitals & world facts" },
];

function resolveTarget(t: TargetScore): number {
  if (t === "random") {
    const opts = [501, 301, 101] as const;
    return opts[Math.floor(Math.random() * opts.length)];
  }
  return t;
}

// ─── Navigation stack types ───────────────────────────────────────────────────

type NavScreen =
  | { id: "root" }
  | { id: "football" }
  | { id: "football-league"; league: League }
  | { id: "football-club";   league: League; club: FootballClub };

// ─── Props ────────────────────────────────────────────────────────────────────

interface LobbyViewProps {
  onStartGame: (slug: string, label: string, targetScore: number, filter?: FootballFilter) => Promise<void> | void;
  onStartDailyChallenge: (slug: string, label: string) => Promise<void> | void;
  dailyChallenges: CategoryChallenge[];
  dailyLoading: boolean;
}

// ─── Main component ───────────────────────────────────────────────────────────

export default function LobbyView({
  onStartGame,
  onStartDailyChallenge,
  dailyChallenges,
  dailyLoading,
}: LobbyViewProps) {
  const [target, setTarget] = useState<TargetScore>(501);
  // Slug of the row that's currently starting a game (null = nothing in flight).
  // Used to disable every row while a start is pending, preventing duplicate POSTs.
  const [starting, setStarting] = useState<string | null>(null);
  const { user, loading } = useAuth();
  const isAdmin = !loading && user?.app_metadata?.role === "admin";

  // Navigation stack — last entry is the currently visible screen
  const [stack, setStack] = useState<NavScreen[]>([{ id: "root" }]);
  // Animation direction: 1 = sliding in from right (push), -1 = sliding in from left (pop)
  const [slideDir, setSlideDir] = useState<1 | -1>(1);
  const [animKey, setAnimKey] = useState(0);

  const push = useCallback((screen: NavScreen) => {
    setSlideDir(1);
    setAnimKey((k) => k + 1);
    setStack((s) => [...s, screen]);
  }, []);

  const pop = useCallback(() => {
    if (stack.length <= 1) return;
    setSlideDir(-1);
    setAnimKey((k) => k + 1);
    setStack((s) => s.slice(0, -1));
  }, [stack.length]);

  const startGame = useCallback(
    async (slug: string, label: string, filter?: FootballFilter) => {
      if (starting) return;
      setStarting(slug);
      try {
        await onStartGame(slug, label, resolveTarget(target), filter);
      } finally {
        setStarting(null);
      }
    },
    [onStartGame, target, starting],
  );

  const startDailyChallenge = useCallback(
    async (slug: string, label: string) => {
      if (starting) return;
      setStarting(slug);
      try {
        await onStartDailyChallenge(slug, label);
      } finally {
        setStarting(null);
      }
    },
    [onStartDailyChallenge, starting],
  );

  const displayTarget = target === "random" ? "?" : String(target);
  const currentScreen = stack[stack.length - 1];

  // Build breadcrumb label for the right-column header
  const breadcrumb = stack
    .slice(1)
    .map((s) => {
      if (s.id === "football") return "Football";
      if (s.id === "football-league") return s.league.name;
      if (s.id === "football-club") return s.club.name;
      return "";
    })
    .join(" › ");

  return (
    <div className="relative min-h-screen bg-bg text-ink flex flex-col font-sans overflow-hidden">
      {/* Background motif — dartboard rings + dart trajectory, barely there */}
      <div className="absolute inset-0 pointer-events-none z-0 text-ink" aria-hidden="true">
        <svg viewBox="0 0 1440 900" className="absolute inset-0 w-full h-full opacity-[0.13]" preserveAspectRatio="xMidYMid slice">
          <g fill="none" stroke="currentColor" strokeWidth="1">
            <circle cx="1190" cy="180" r="60" />
            <circle cx="1190" cy="180" r="130" />
            <circle cx="1190" cy="180" r="210" />
            <circle cx="1190" cy="180" r="300" />
            <circle cx="1190" cy="180" r="400" />
          </g>
          <circle cx="1190" cy="180" r="5" fill="currentColor" />
          <path
            d="M-100,640 C300,540 800,820 1190,180"
            stroke="currentColor"
            strokeWidth="1.5"
            fill="none"
            className="animate-draw"
          />
        </svg>
      </div>

      {/* Header */}
      <header className="relative z-10 flex items-center justify-between px-5 md:px-10 py-4 border-b border-line">
        <div className="flex items-center gap-3">
          <span className="bullseye" aria-hidden="true" />
          <span className="font-display font-extrabold text-lg tracking-tight leading-none">
            TRIVIA <span className="text-accent">501</span>
          </span>
          <span className="kicker hidden sm:block ml-2">The trivia darts championship</span>
        </div>
        <div className="flex items-center gap-3">
          {isAdmin && (
            <a href="/admin" className="kicker hover:text-ink transition-colors">
              Admin
            </a>
          )}
          <ThemeToggle />
          <LoginButton />
        </div>
      </header>

      {/* Two-column layout */}
      <main className="relative z-10 flex-1 flex flex-col md:flex-row">

        {/* ── Left column: target score + daily challenges ── */}
        <div className="flex-1 flex flex-col px-5 md:px-10 py-8 md:border-r border-line min-w-0">

          {/* Target Score */}
          <div className="mb-10">
            <div className="kicker mb-4">Target Score</div>
            <div className="flex gap-2 mb-6 flex-wrap">
              {TARGET_OPTIONS.map((opt) => (
                <button
                  key={opt}
                  onClick={() => setTarget(opt)}
                  aria-pressed={target === opt}
                  className={`font-mono text-[11px] tracking-[0.15em] px-5 py-2.5 rounded-full border transition-all duration-200 ${
                    target === opt
                      ? "bg-ink text-bg border-ink"
                      : "bg-transparent text-muted border-line hover:border-line-strong hover:text-ink"
                  }`}
                >
                  {opt === "random" ? "RND" : opt}
                </button>
              ))}
            </div>
            <div
              className="display-num select-none transition-all duration-300"
              style={{ fontSize: "clamp(88px, 12vw, 180px)" }}
            >
              {displayTarget}
            </div>
            {target === "random" && (
              <div className="kicker mt-2">Picked randomly when you start</div>
            )}
          </div>

          {/* Daily Challenges */}
          {!dailyLoading && dailyChallenges.length > 0 && (
            <div className="mb-8">
              <div className="flex items-center gap-2.5 mb-3">
                <span className="w-2 h-2 rounded-full bg-gold" aria-hidden="true" />
                <span className="kicker">Today&apos;s Challenges</span>
                <span className="ml-auto kicker opacity-60 hidden sm:block">
                  Score once · Share with friends
                </span>
              </div>
              <div className="flex gap-3 overflow-x-auto pb-2 scrollbar-thin">
                {dailyChallenges.map((dc) => {
                  const isThisStarting = starting === dc.categorySlug;
                  const lock = dc.lockState;
                  const isCompleted = lock?.state === "completed";
                  const isInProgress = lock?.state === "in_progress";

                  if (isCompleted) {
                    return (
                      <a
                        key={dc.categorySlug}
                        href={`/daily/${dc.categorySlug}`}
                        className="group flex-shrink-0 flex flex-col bg-surface border border-line rounded-md p-4 w-56 text-left transition-all duration-200 opacity-60 hover:opacity-80"
                      >
                        <div className="flex items-baseline justify-between mb-2">
                          <span className="font-display font-bold text-sm">{dc.categoryName}</span>
                          <span className="font-mono text-[9px] tracking-[0.2em] text-gold">PLAYED</span>
                        </div>
                        <div className="display-num text-[34px] mb-1.5">
                          {dc.startingScore}
                        </div>
                        <div className="font-sans text-[12px] text-muted leading-snug line-clamp-2 mb-3">
                          {dc.questionText || "Loading..."}
                        </div>
                        <div className="mt-auto flex items-center justify-between">
                          <span className="font-mono text-[9px] tracking-[0.2em] text-muted uppercase">
                            View result
                          </span>
                          <span className="font-display font-bold text-muted transition-transform group-hover:translate-x-0.5">→</span>
                        </div>
                      </a>
                    );
                  }

                  return (
                    <button
                      key={dc.categorySlug}
                      onClick={() => startDailyChallenge(dc.categorySlug, dc.categoryName)}
                      disabled={starting !== null}
                      className="group flex-shrink-0 flex flex-col bg-surface border border-line rounded-md p-4 w-56 text-left transition-all duration-200 hover:-translate-y-0.5 hover:border-line-strong hover:shadow-[var(--shadow-card)] disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:translate-y-0"
                    >
                      <div className="flex items-baseline justify-between mb-2">
                        <span className="font-display font-bold text-sm">{dc.categoryName}</span>
                        <span className="font-mono text-[9px] tracking-[0.2em] text-gold">
                          {isInProgress ? "IN PROGRESS" : "DAILY"}
                        </span>
                      </div>
                      <div className="display-num text-[34px] mb-1.5">
                        {dc.startingScore}
                      </div>
                      <div className="font-sans text-[12px] text-muted leading-snug line-clamp-2 mb-3">
                        {dc.questionText || "Loading..."}
                      </div>
                      <div className="mt-auto flex items-center justify-between">
                        <span className="font-mono text-[9px] tracking-[0.2em] text-accent uppercase">
                          {isThisStarting ? "Starting…"
                          : isInProgress ? "Resume"
                          : "Play now"}
                        </span>
                        <span className="font-display font-bold text-accent transition-transform group-hover:translate-x-0.5">→</span>
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>
          )}

        </div>

        {/* ── Right column: drill-down navigator ── */}
        <div className="w-full md:w-96 lg:w-[440px] flex flex-col px-5 md:px-8 py-8 overflow-hidden relative">

          {/* Back + breadcrumb */}
          {stack.length > 1 && (
            <button
              onClick={pop}
              className="flex items-center gap-2 kicker mb-4 hover:text-ink transition-colors self-start"
            >
              ← {breadcrumb || "Back"}
            </button>
          )}

          {/* Animated screen area */}
          <div key={animKey} className={`flex-1 ${slideDir === 1 ? "animate-nav-push" : "animate-nav-pop"}`}>
            <NavScreenRenderer
              screen={currentScreen}
              onPush={push}
              onStartGame={startGame}
              starting={starting}
            />
          </div>
        </div>
      </main>
    </div>
  );
}

// ─── Screen renderer (delegates to the right component per screen) ────────────

function NavScreenRenderer({
  screen,
  onPush,
  onStartGame,
  starting,
}: {
  screen: NavScreen;
  onPush: (s: NavScreen) => void;
  onStartGame: (slug: string, label: string, filter?: FootballFilter) => void;
  starting: string | null;
}) {
  if (screen.id === "root") {
    return <RootScreen onPush={onPush} onStartGame={onStartGame} starting={starting} />;
  }
  if (screen.id === "football") {
    return <FootballScreen onPush={onPush} onStartGame={onStartGame} starting={starting} />;
  }
  if (screen.id === "football-league") {
    return <LeagueScreen league={screen.league} onPush={onPush} onStartGame={onStartGame} starting={starting} />;
  }
  if (screen.id === "football-club") {
    return <ClubScreen league={screen.league} club={screen.club} onStartGame={onStartGame} starting={starting} />;
  }
  return null;
}

// ─── Screen: root — all categories ───────────────────────────────────────────

function RootScreen({
  onPush,
  onStartGame,
  starting,
}: {
  onPush: (s: NavScreen) => void;
  onStartGame: (slug: string, label: string, filter?: FootballFilter) => void;
  starting: string | null;
}) {
  const isStarting = starting !== null;
  return (
    <>
      <div className="kicker mb-5">Choose Your Board</div>

      {/* Football — drill-down */}
      <NavRow
        name="Football"
        sub="Goals · assists · 5 leagues"
        onClick={() => onPush({ id: "football" })}
        hasChildren
        disabled={isStarting}
      />

      {/* Other categories — one click */}
      {OTHER_CATEGORIES.map((cat) => (
        <NavRow
          key={cat.id}
          name={cat.name}
          sub={cat.description}
          onClick={() => onStartGame(cat.id, cat.name)}
          disabled={isStarting}
          loading={starting === cat.id}
        />
      ))}

      <div className="mt-8">
        <HowToPlayPanel />
      </div>
    </>
  );
}

// ─── Screen: football root ────────────────────────────────────────────────────

function FootballScreen({
  onPush,
  onStartGame,
  starting,
}: {
  onPush: (s: NavScreen) => void;
  onStartGame: (slug: string, label: string, filter?: FootballFilter) => void;
  starting: string | null;
}) {
  const isStarting = starting !== null;
  return (
    <>
      <div className="kicker mb-5">Football</div>

      <NavRow
        random
        name="Random Question"
        sub="Any club, any league, any stat"
        onClick={() => onStartGame("football", "Football — Random", { scope: "random_any" })}
        disabled={isStarting}
        loading={starting === "football"}
      />

      <NavRow
        random
        name="Random League Question"
        sub="League-wide stat, picked at random"
        onClick={() => onStartGame("football", "Football — Random League", { scope: "random_league_level" })}
        disabled={isStarting}
      />

      <NavDivider label="or pick a league" />

      {LEAGUES.map((league) => (
        <NavRow
          key={league.id}
          name={league.name}
          onClick={() => onPush({ id: "football-league", league })}
          hasChildren
          disabled={isStarting}
        />
      ))}
    </>
  );
}

// ─── Screen: league ───────────────────────────────────────────────────────────

function LeagueScreen({
  league,
  onPush,
  onStartGame,
  starting,
}: {
  league: League;
  onPush: (s: NavScreen) => void;
  onStartGame: (slug: string, label: string, filter?: FootballFilter) => void;
  starting: string | null;
}) {
  const isStarting = starting !== null;
  const [clubs, setClubs] = useState<FootballClub[]>([]);
  const [loadingClubs, setLoadingClubs] = useState(true);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    setLoadingClubs(true);
    fetchClubs(league.id)
      .then((data) => { if (mounted.current) { setClubs(data); setLoadingClubs(false); } })
      .catch(() => { if (mounted.current) setLoadingClubs(false); });
    return () => { mounted.current = false; };
  }, [league.id]);

  return (
    <>
      <div className="kicker mb-5">{league.name}</div>

      {/* League-scope questions */}
      <NavRow
        random
        name="League Questions"
        sub={`Stats across the full ${league.name}`}
        onClick={() => onStartGame(`football:${league.id}`, `Football › ${league.name} › League`, {
          scope: "league", league: league.id,
        })}
        disabled={isStarting}
        loading={starting === `football:${league.id}`}
      />

      {/* Stat type drill-down for league */}
      {STAT_TYPES.map((stat) => {
        const slug = `football:${league.id}:league:${stat.id}`;
        return (
          <NavRow
            key={`league-${stat.id}`}
            name={stat.name}
            sub={stat.sub}
            small
            onClick={() => onStartGame(
              slug,
              `Football › ${league.name} › ${stat.name}`,
              { scope: "league", league: league.id, statType: stat.id },
            )}
            disabled={isStarting}
            loading={starting === slug}
          />
        );
      })}

      <NavDivider label="or pick a club" />

      <NavRow
        random
        name="Random Club"
        sub={`Any club from the ${league.name}`}
        onClick={() => onStartGame(`football:${league.id}:random`, `Football › ${league.name} › Random Club`, {
          scope: "random_club_level", league: league.id,
        })}
        disabled={isStarting}
        loading={starting === `football:${league.id}:random`}
      />

      {loadingClubs ? (
        <div className="kicker py-4 animate-pulse">Loading clubs…</div>
      ) : clubs.length === 0 ? (
        <div className="kicker py-4">No clubs available yet — data coming soon.</div>
      ) : (
        clubs.map((club) => (
          <NavRow
            key={club.id}
            name={club.name}
            onClick={() => onPush({ id: "football-club", league, club })}
            hasChildren
            disabled={isStarting}
          />
        ))
      )}
    </>
  );
}

// ─── Screen: club stat picker ─────────────────────────────────────────────────

function ClubScreen({
  league,
  club,
  onStartGame,
  starting,
}: {
  league: League;
  club: FootballClub;
  onStartGame: (slug: string, label: string, filter?: FootballFilter) => void;
  starting: string | null;
}) {
  const isStarting = starting !== null;
  const randomSlug = `football:${league.id}:${club.id}`;
  return (
    <>
      <div className="kicker mb-5">{club.name}</div>

      <NavRow
        random
        name="Random Question"
        sub="Any stat type for this club"
        onClick={() => onStartGame(
          randomSlug,
          `Football › ${league.name} › ${club.name}`,
          { scope: "club", league: league.id, club: club.id },
        )}
        disabled={isStarting}
        loading={starting === randomSlug}
      />

      <NavDivider label="or pick a stat" />

      {STAT_TYPES.map((stat) => {
        const slug = `football:${league.id}:${club.id}:${stat.id}`;
        return (
          <NavRow
            key={stat.id}
            name={stat.name}
            sub={stat.sub}
            small
            onClick={() => onStartGame(
              slug,
              `Football › ${league.name} › ${club.name} › ${stat.name}`,
              { scope: "club", league: league.id, club: club.id, statType: stat.id },
            )}
            disabled={isStarting}
            loading={starting === slug}
          />
        );
      })}
    </>
  );
}

// ─── Shared pieces ────────────────────────────────────────────────────────────

function NavDivider({ label }: { label: string }) {
  return (
    <div className="py-3 flex items-center gap-3">
      <div className="flex-1 border-t border-line" />
      <span className="kicker text-[9px]">{label}</span>
      <div className="flex-1 border-t border-line" />
    </div>
  );
}

function NavRow({
  random = false,
  name,
  sub,
  onClick,
  hasChildren = false,
  small = false,
  disabled = false,
  loading = false,
}: {
  /** Marks "surprise me" rows with a die glyph instead of the accent tick. */
  random?: boolean;
  name: string;
  sub?: string;
  onClick: () => void;
  hasChildren?: boolean;
  small?: boolean;
  disabled?: boolean;
  loading?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className="group flex items-center gap-4 py-3.5 px-2 -mx-2 rounded-sm border-b border-line hover:bg-surface transition-colors text-left w-full disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent"
    >
      {random ? (
        <span className="text-accent font-display font-bold text-lg leading-none flex-shrink-0 w-4 text-center" aria-hidden="true">
          ✦
        </span>
      ) : (
        <span className="w-1 self-stretch rounded-full flex-shrink-0 bg-line group-hover:bg-accent transition-colors" aria-hidden="true" />
      )}

      <div className="flex-1 min-w-0">
        <div className={`font-display font-bold leading-tight ${small ? "text-base" : "text-xl"}`}>
          {name}
          {loading && <span className="ml-2 kicker">Starting…</span>}
        </div>
        {sub && <div className="hint mt-0.5 text-[10px]">{sub}</div>}
      </div>

      <span className="font-display font-bold text-base text-muted group-hover:text-accent group-hover:translate-x-0.5 transition-all flex-shrink-0" aria-hidden="true">
        {loading ? "…" : hasChildren ? "→" : "↵"}
      </span>
    </button>
  );
}
