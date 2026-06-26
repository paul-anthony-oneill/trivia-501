"use client";

import NavRow from "@/components/game/lobby/NavRow";
import NavDivider from "@/components/game/lobby/NavDivider";
import { type NavScreen, type League, STAT_TYPES } from "@/components/game/lobby/types";
import type { FootballClub, FootballFilter } from "@/lib/api/footballApi";

interface ClubScreenProps {
  league: League;
  club: FootballClub;
  onStartGame: (slug: string, label: string, filter?: FootballFilter) => void;
  starting: string | null;
}

export default function ClubScreen({ league, club, onStartGame, starting }: ClubScreenProps) {
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
