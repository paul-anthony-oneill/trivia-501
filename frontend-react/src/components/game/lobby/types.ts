import type { FootballClub } from "@/lib/api/footballApi";

// ─── Target score ──────────────────────────────────────────────────────────

export type TargetScore = 501 | 301 | 101 | "random";
export const TARGET_OPTIONS: TargetScore[] = [501, 301, 101, "random"];

// ─── Static data ───────────────────────────────────────────────────────────

export const LEAGUES = [
  { id: "premier-league",   name: "Premier League" },
  { id: "la-liga",          name: "La Liga" },
  { id: "bundesliga",       name: "Bundesliga" },
  { id: "serie-a",          name: "Serie A" },
  { id: "champions-league", name: "Champions League" },
] as const;
export type League = (typeof LEAGUES)[number];

export const STAT_TYPES = [
  { id: "goals",                    name: "Goals",                          sub: "Goals scored since 2000" },
  { id: "assists",                  name: "Assists",                        sub: "Goal assists since 2000" },
  { id: "appearances",              name: "Appearances",                    sub: "Games played since 2000" },
  { id: "goals_assists",            name: "Goals + Assists",                sub: "Combined total" },
  { id: "goals_appearances",        name: "Goals + Appearances",            sub: "Combined total" },
  { id: "assists_appearances",      name: "Assists + Appearances",          sub: "Combined total" },
  { id: "goals_assists_appearances", name: "Goals + Assists + Appearances", sub: "All three combined" },
] as const;

export const OTHER_CATEGORIES = [
  { id: "film",      name: "Film",      description: "Worldwide box office hits" },
  { id: "geography", name: "Geography", description: "Populations, capitals & world facts" },
];

// ─── Helpers ───────────────────────────────────────────────────────────────

export function resolveTarget(t: TargetScore): number {
  if (t === "random") {
    const opts = [501, 301, 101] as const;
    return opts[Math.floor(Math.random() * opts.length)];
  }
  return t;
}

// ─── Navigation stack types ────────────────────────────────────────────────

export type NavScreen =
  | { id: "root" }
  | { id: "football" }
  | { id: "football-league"; league: League }
  | { id: "football-club";   league: League; club: FootballClub };
