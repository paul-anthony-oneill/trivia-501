"use client";

import { useState, useCallback, useEffect, useRef, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import dynamic from "next/dynamic";
import NavRow from "@/components/game/lobby/NavRow";
import NavDivider from "@/components/game/lobby/NavDivider";
import ThemeToggle from "@/components/ui/ThemeToggle";
import { gameApiClient } from "@/lib/api/GameApiClient";
import { saveGameState } from "@/hooks/useGamePersistence";
import { useToast } from "@/context/ToastContext";
import { fetchClubs, type FootballClub, type FootballFilter } from "@/lib/api/footballApi";
import {
  type NavScreen,
  type League,
  LEAGUES,
  STAT_TYPES,
} from "@/components/game/lobby/types";

const LoginButton = dynamic(() => import("@/components/auth/LoginButton"), { ssr: false });

// ─── Screen components ─────────────────────────────────────────────────────

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
    <div className="flex flex-col lg:flex-row lg:gap-8">
      {/* Left pane: primary actions */}
      <div className="lg:flex-[1.2]">
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
      </div>

      {/* Right pane: league list */}
      <div className="lg:flex-1 lg:border-l lg:border-line lg:pl-8">
        <span className="block font-display font-bold text-sm text-muted tracking-[0.12em] uppercase mb-3">
          Leagues
        </span>

        {LEAGUES.map((league) => (
          <NavRow
            key={league.id}
            name={league.name}
            onClick={() => onPush({ id: "football-league", league })}
            hasChildren
            disabled={isStarting}
          />
        ))}
      </div>
    </div>
  );
}

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
    <div className="flex flex-col lg:flex-row lg:gap-8 lg:h-[calc(100vh-12rem)]">
      {/* Left pane: league-wide actions */}
      <div className="lg:flex-[1.2] lg:overflow-y-auto">
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
      </div>

      {/* Right pane: clubs */}
      <div className="lg:flex-1 lg:overflow-y-auto lg:border-l lg:border-line lg:pl-8">
        <span className="block font-display font-bold text-sm text-muted tracking-[0.12em] uppercase mb-3">
          Clubs
        </span>

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
      </div>
    </div>
  );
}

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

// ─── Screen renderer ───────────────────────────────────────────────────────

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

// ─── Inner page (reads search params, must be inside Suspense) ─────────────

function FootballPageInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { addToast } = useToast();

  // Read target from URL, default to 501
  const targetParam = searchParams.get("target");
  const targetScore = targetParam === "301" ? 301 : targetParam === "101" ? 101 : 501;

  const [starting, setStarting] = useState<string | null>(null);

  // Navigation stack — last entry is the currently visible screen
  const [stack, setStack] = useState<NavScreen[]>([{ id: "football" }]);
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

  // Sync browser back button with drill-down stack
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

  const startGame = useCallback(
    async (slug: string, label: string, filter?: FootballFilter) => {
      if (starting) return;
      setStarting(slug);
      try {
        const game = await gameApiClient.startFreePlay(slug, targetScore, filter);
        saveGameState(game.gameId, label, "freeplay");
        router.push("/");
      } catch (err: any) {
        addToast(err?.message ?? "Failed to start game", "error");
      } finally {
        setStarting(null);
      }
    },
    [gameApiClient, targetScore, router, addToast, starting],
  );

  const currentScreen = stack[stack.length - 1];

  // Breadcrumb label for the back button
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
    <div className="relative min-h-screen bg-bg text-ink flex flex-col font-sans">
      {/* Header */}
      <header className="relative z-10 flex items-center justify-between px-5 md:px-10 py-4 border-b border-line">
        <div className="flex items-center gap-3">
          {stack.length > 1 ? (
            <button
              onClick={pop}
              className="font-mono text-lg text-muted hover:text-accent hover:-translate-x-0.5 transition-all"
              aria-label="Back"
            >
              ←
            </button>
          ) : (
            <Link href="/freeplay" className="font-mono text-lg text-muted hover:text-accent hover:-translate-x-0.5 transition-all" aria-label="Back to Free Play">
              ←
            </Link>
          )}
          <span className="bullseye" aria-hidden="true" />
          <span className="font-display font-extrabold text-lg tracking-tight leading-none">
            TRIVIA <span className="text-accent">501</span>
          </span>
          <span className="kicker hidden sm:block ml-2">Football</span>
        </div>
        <div className="flex items-center gap-3">
          <span className="font-mono text-[11px] text-muted tabular-nums">
            Target: {targetScore}
          </span>
          <ThemeToggle />
          <LoginButton />
        </div>
      </header>

      <main className="relative z-10 flex-1 flex flex-col px-5 md:px-10 py-6">
        {/* Breadcrumb back button when drilled down */}
        {stack.length > 1 && (
          <button
            onClick={pop}
            className="group flex items-center gap-4 py-3.5 px-2 -mx-2 rounded-sm border-b border-line hover:bg-surface transition-colors text-left w-full mb-4"
          >
            <span aria-hidden="true" className="font-mono text-lg text-muted group-hover:text-accent group-hover:-translate-x-0.5 transition-all">←</span>
            <span className="font-display font-bold text-xl text-ink">{breadcrumb || "Back"}</span>
          </button>
        )}

        {/* Animated content area */}
        <div key={animKey} className={slideDir === 1 ? "animate-nav-push" : "animate-nav-pop"}>
          <NavScreenRenderer
            screen={currentScreen!}
            onPush={push}
            onStartGame={startGame}
            starting={starting}
          />
        </div>
      </main>
    </div>
  );
}

// ─── Page (wraps inner in Suspense for useSearchParams) ─────────────────────

export default function FootballPage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen flex items-center justify-center bg-bg">
        <div className="animate-spin-slow rounded-full h-10 w-10 border-2 border-line border-t-accent" />
      </div>
    }>
      <FootballPageInner />
    </Suspense>
  );
}
