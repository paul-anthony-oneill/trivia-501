"use client";

import FootballScreen from "./FootballScreen";
import LeagueScreen from "./LeagueScreen";
import ClubScreen from "./ClubScreen";
import type { NavScreen } from "@/components/game/lobby/types";
import type { FootballFilter } from "@/lib/api/footballApi";

interface NavScreenRendererProps {
  screen: NavScreen;
  onPush: (s: NavScreen) => void;
  onStartGame: (slug: string, label: string, filter?: FootballFilter) => void;
  starting: string | null;
}

export default function NavScreenRenderer({ screen, onPush, onStartGame, starting }: NavScreenRendererProps) {
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
