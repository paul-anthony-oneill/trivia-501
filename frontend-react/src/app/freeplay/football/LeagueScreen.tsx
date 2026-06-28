"use client";

import { useState, useEffect } from "react";
import NavRow from "@/components/game/lobby/NavRow";
import { fetchClubs, type FootballClub, type FootballFilter } from "@/lib/api/footballApi";
import { type NavScreen, type League, STAT_TYPES } from "@/components/game/lobby/types";

interface LeagueScreenProps {
  league: League;
  onPush: (s: NavScreen) => void;
  onStartGame: (slug: string, label: string, filter?: FootballFilter) => void;
  starting: string | null;
}

export default function LeagueScreen({ league, onPush, onStartGame, starting }: LeagueScreenProps) {
  const isStarting = starting !== null;
  const [clubs, setClubs] = useState<FootballClub[]>([]);
  const [loadingClubs, setLoadingClubs] = useState(true);

  useEffect(() => {
    setLoadingClubs(true);
    fetchClubs(league.id)
      .then((data) => { setClubs(data); setLoadingClubs(false); })
      .catch(() => setLoadingClubs(false));
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
