"use client";

import { useState, useCallback, useEffect, useRef } from "react";
import dynamic from "next/dynamic";
const LoginButton = dynamic(() => import("@/components/auth/LoginButton"), { ssr: false });
import HowToPlayPanel from "../HowToPlayPanel";
import ThemeToggle from "@/components/ui/ThemeToggle";
import { useAuth } from "@/context/AuthContext";
import { fetchClubs, type FootballClub, type FootballFilter } from "@/lib/api/footballApi";
import type { CategoryChallenge } from "@/hooks/useDailyChallenge";
import ConfirmDialog from "@/components/ui/ConfirmDialog";

// ─── Static data ──────────────────────────────────────────────────────────────

const DARTBOARD_SVG = (
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
);

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
  dailyError: string | null;
  onRetryDailies: () => void;
}

// ─── Main component ───────────────────────────────────────────────────────────

export default function LobbyView({
  onStartGame,
  onStartDailyChallenge,
  dailyChallenges,
  dailyLoading,
  dailyError,
  onRetryDailies,
}: LobbyViewProps) {
  const [target, setTarget] = useState<TargetScore>(501);
  // Slug of the row that's currently starting a game (null = nothing in flight).
  // Used to disable every row while a start is pending, preventing duplicate POSTs.
  const [starting, setStarting] = useState<string | null>(null);
  // Pending daily challenge — set when a card is clicked; cleared on confirm or cancel.
  const [pendingDaily, setPendingDaily] = useState<{ slug: string; label: string } | null>(null);
  const [timeUntilReset, setTimeUntilReset] = useState("");
  const { user, loading } = useAuth();
  const isAdmin = !loading && user?.app_metadata?.role === "admin";

  // Navigation stack — last entry is the currently visible screen
  const [stack, setStack] = useState<NavScreen[]>([{ id: "root" }]);
  // Animation direction: 1 = sliding in from right (push), -1 = sliding in from left (pop)
  const [slideDir, setSlideDir] = useState<1 | -1>(1);
  const [animKey, setAnimKey] = useState(0);

  const push = useCallback((screen: NavScreen) => {
    window.history.pushState(null, "", "");
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

  // Sync browser back button with drill-down navigation stack
  useEffect(() => {
    const onPopState = () => {
      if (stack.length <= 1) return;
      setSlideDir(-1);
      setAnimKey((k) => k + 1);
      setStack((s) => s.slice(0, -1));
    };
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, [stack.length]);

  // Countdown to next daily challenge reset (midnight UTC)
  useEffect(() => {
    const tick = () => {
      const now = new Date();
      const next = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1));
      setTimeUntilReset(new Date(next.getTime() - now.getTime()).toISOString().slice(11, 19));
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, []);

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
        {DARTBOARD_SVG}
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

      {/* Single-column stacked layout */}
      <main className="relative z-10 flex-1 flex flex-col px-5 md:px-10 py-8">

        {/* ── Section 1: Daily Challenges ── */}
        <section className="mb-10">
          <div className="flex items-center gap-2.5 mb-4">
            <span className="w-2 h-2 rounded-full bg-gold" aria-hidden="true" />
            <span className="kicker">Today&apos;s Challenges</span>
            {timeUntilReset && (
              <span className="font-mono text-[11px] text-muted tabular-nums">
                Resets in {timeUntilReset}
              </span>
            )}
          </div>

          {dailyLoading && (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
              {[1, 2, 3].map((i) => (
                <div
                  key={i}
                  className="bg-surface border border-line rounded-md p-6 animate-pulse"
                >
                  <div className="h-4 bg-line rounded w-3/4 mb-3" />
                  <div className="h-8 bg-line rounded w-1/2 mb-3" />
                  <div className="h-3 bg-line rounded w-full mb-2" />
                  <div className="h-3 bg-line rounded w-5/6" />
                </div>
              ))}
            </div>
          )}

          {dailyError && !dailyLoading && (
            <div className="flex items-center gap-3 bg-surface border border-line rounded-md px-4 py-3 text-sm text-muted">
              <span className="flex-1">Couldn&apos;t load today&apos;s challenges.</span>
              <button
                onClick={onRetryDailies}
                className="kicker text-accent hover:text-ink transition-colors shrink-0"
              >
                Retry
              </button>
            </div>
          )}

          {!dailyLoading && !dailyError && dailyChallenges.length > 0 && (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
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
                      className="group flex flex-col bg-surface border border-line rounded-md p-6 text-left transition-all duration-200 opacity-60 hover:opacity-80"
                    >
                      <div className="flex items-baseline justify-between mb-2">
                        <span className="font-display font-bold text-base">{dc.categoryName}</span>
                        <span className="font-mono text-[10px] tracking-[0.2em] text-gold">PLAYED</span>
                      </div>
                      <div className="display-num text-[56px] mb-2">
                        {dc.startingScore}
                      </div>
                      <div className="font-sans text-sm text-muted leading-snug line-clamp-2 mb-4">
                        {dc.questionText || "Loading..."}
                      </div>
                      <div className="mt-auto flex items-center justify-between">
                        <span className="font-mono text-[10px] tracking-[0.2em] text-muted uppercase">
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
                    onClick={() => {
                      if (isInProgress) {
                        startDailyChallenge(dc.categorySlug, dc.categoryName);
                      } else {
                        setPendingDaily({ slug: dc.categorySlug, label: dc.categoryName });
                      }
                    }}
                    disabled={starting !== null}
                    className="group flex flex-col bg-surface border border-line rounded-md p-6 text-left transition-all duration-200 hover:-translate-y-0.5 hover:border-line-strong hover:shadow-[var(--shadow-card)] disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:translate-y-0"
                  >
                    <div className="flex items-baseline justify-between mb-2">
                      <span className="font-display font-bold text-base">{dc.categoryName}</span>
                      <span className="font-mono text-[10px] tracking-[0.2em] text-gold">
                        {isInProgress ? "IN PROGRESS" : "DAILY"}
                      </span>
                    </div>
                    <div className="display-num text-[56px] mb-2">
                      {dc.startingScore}
                    </div>
                    <div className="font-sans text-sm text-muted leading-snug line-clamp-2 mb-4">
                      {dc.questionText || "Loading..."}
                    </div>
                    <div className="mt-auto flex items-center justify-between">
                      <span className="font-mono text-[10px] tracking-[0.2em] text-accent uppercase">
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
          )}

        </section>

        {/* ── Section 2: Build Your Own Game ── */}
        <section className="border-t border-line pt-8">
          <div className="mb-5 p-4 rounded-lg bg-ink/[0.04]">
            <div className="flex items-baseline gap-2 mb-3">
              <span className="font-display font-bold text-lg text-ink">Build Your Own Game</span>
              <span className="font-mono text-[10px] tracking-[0.12em] text-muted">— Free Play —</span>
            </div>
            <div className="flex items-center gap-2.5 flex-wrap">
              <span className="font-mono text-[11px] text-muted">Starting score:</span>
              {TARGET_OPTIONS.map((opt) => (
                <button
                  key={opt}
                  onClick={() => setTarget(opt)}
                  aria-pressed={target === opt}
                  className={`font-mono text-sm font-bold tracking-[0.06em] px-4 py-2 rounded-full border-2 transition-all duration-200 ${
                    target === opt
                      ? "bg-ink text-bg border-ink"
                      : "bg-transparent text-muted border-line hover:border-line-strong hover:text-ink"
                  }`}
                >
                  {opt === "random" ? "RND" : opt}
                </button>
              ))}
            </div>
          </div>

          {/* Back + breadcrumb — doubles as section title */}
          {stack.length > 1 && (
            <button
              onClick={pop}
              className="inline-flex items-center gap-2.5 group mb-5 hover:-translate-x-0.5 transition-transform"
            >
              <span aria-hidden="true" className="font-mono text-muted group-hover:text-ink transition-colors">←</span>
              <span className="font-display font-bold text-xl text-ink">{breadcrumb || "Back"}</span>
            </button>
          )}

          {/* Animated content area */}
          <div key={animKey} className={slideDir === 1 ? "animate-nav-push" : "animate-nav-pop"}>
            <NavScreenRenderer
              screen={currentScreen}
              onPush={push}
              onStartGame={startGame}
              starting={starting}
            />
          </div>
        </section>
      </main>

      <ConfirmDialog
        open={pendingDaily !== null}
        title={`Play today's ${pendingDaily?.label} challenge?`}
        message="You only get one attempt per day. Once you start, this is your shot."
        confirmText="Let's go"
        cancelText="Not yet"
        type="info"
        onConfirm={() => {
          if (pendingDaily) {
            startDailyChallenge(pendingDaily.slug, pendingDaily.label);
          }
          setPendingDaily(null);
        }}
        onCancel={() => setPendingDaily(null)}
      />
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
  const [showSpecial, setShowSpecial] = useState(false);

  return (
    <div className="flex flex-col gap-4">
      {/* Football — full-width primary card */}
      <CategoryCard
        name="Football"
        description="Goals, assists, appearances across 5 leagues"
        onClick={() => onPush({ id: "football" })}
        hasChildren
        disabled={isStarting}
      />

      {/* Special Categories — collapsible accordion */}
      <div className="bg-surface border border-line rounded-md overflow-hidden">
        <button
          onClick={() => setShowSpecial((v) => !v)}
          className="w-full flex items-center justify-between p-5 text-left hover:bg-ink/[0.02] transition-colors"
        >
          <div>
            <span className="font-display font-bold text-lg">Special Categories</span>
            <span className="hint text-[10px] block mt-0.5">Film · Geography</span>
          </div>
          <span className={`font-display font-bold text-muted text-lg transition-transform duration-200 ${showSpecial ? "rotate-180" : ""}`} aria-hidden="true">
            ▼
          </span>
        </button>

        {showSpecial && (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5 px-5 pb-5">
            {OTHER_CATEGORIES.map((cat) => (
              <CategoryCard
                key={cat.id}
                name={cat.name}
                description={cat.description}
                onClick={() => onStartGame(cat.id, cat.name)}
                disabled={isStarting}
                loading={starting === cat.id}
              />
            ))}
          </div>
        )}
      </div>

      <div className="mt-4">
        <HowToPlayPanel />
      </div>
    </div>
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
      {/* name is now shown in the back button above */}

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
      {/* league name is now shown in the back button above */}

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
      {/* club name is now shown in the back button above */}

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

function CategoryCard({
  name,
  description,
  onClick,
  hasChildren = false,
  disabled = false,
  loading = false,
}: {
  name: string;
  description: string;
  onClick: () => void;
  hasChildren?: boolean;
  disabled?: boolean;
  loading?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className="group flex flex-col bg-surface border border-line rounded-md p-5 text-left transition-all duration-200 hover:-translate-y-0.5 hover:border-line-strong hover:shadow-[var(--shadow-card)] disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:translate-y-0"
    >
      <span className="font-display font-bold text-lg leading-tight mb-1">
        {name}
        {loading && <span className="ml-2 kicker">Starting…</span>}
      </span>
      <span className="hint text-[10px] leading-snug">{description}</span>
      <span className="mt-3 font-display font-bold text-base text-muted group-hover:text-accent group-hover:translate-x-0.5 transition-all self-end" aria-hidden="true">
        {loading ? "…" : hasChildren ? "→ Browse" : "↵ Play"}
      </span>
    </button>
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
