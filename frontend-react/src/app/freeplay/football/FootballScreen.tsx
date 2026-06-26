"use client";

import NavRow from "@/components/game/lobby/NavRow";
import { LEAGUES, type NavScreen } from "@/components/game/lobby/types";
import type { FootballFilter } from "@/lib/api/footballApi";

interface FootballScreenProps {
  onPush: (s: NavScreen) => void;
  onStartGame: (slug: string, label: string, filter?: FootballFilter) => void;
  starting: string | null;
}

export default function FootballScreen({ onPush, onStartGame, starting }: FootballScreenProps) {
  const isStarting = starting !== null;
  return (
    <div className="flex flex-col lg:flex-row lg:gap-8">
      {/* Left pane: primary actions */}
      <div className="lg:flex-[1.2]">
        <NavRow
          random
          name="Random Question"
          sub="Any club, any league, any stat"
          onClick={() => onStartGame("football:random_any", "Football — Random", { scope: "random_any" })}
          disabled={isStarting}
          loading={starting === "football:random_any"}
        />

        <NavRow
          random
          name="Random League Question"
          sub="League-wide stat, picked at random"
          onClick={() => onStartGame("football:random_league", "Football — Random League", { scope: "random_league_level" })}
          disabled={isStarting}
          loading={starting === "football:random_league"}
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
