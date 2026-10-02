import { gameApiClient } from "@/lib/api/GameApiClient";
import { resolveTarget, type TargetScore } from "@/components/game/lobby/types";
import type { FootballFilter } from "@/lib/api/footballApi";
import type { GameStateResponse } from "@/hooks/useGameLoop.types";

/**
 * Game-start module. The only place a game gets started: abandons the
 * previous Free Play game, POSTs, and persists `{gameId, spec}` to
 * sessionStorage so `/` (useGameLoop) can pick it up — on mount for pages
 * that navigate there, or directly for starts made on `/` itself.
 *
 * Callers decide whether to navigate. See CONTEXT.md for Game Spec terms.
 */

export type GameSpec =
  | { mode: "daily"; categorySlug: string; breadcrumb: string[] }
  | {
      mode: "freeplay";
      categorySlug: string;
      breadcrumb: string[];
      /** The Target Choice — "random" re-rolls on every start (incl. Play Again). */
      target: TargetScore;
      filter?: FootballFilter;
    };

export interface SavedGame {
  gameId: string;
  spec: GameSpec;
}

const STORAGE_KEY = "activeGameState";

export async function startGame(spec: GameSpec): Promise<GameStateResponse> {
  const previous = loadSavedGame();

  const game =
    spec.mode === "daily"
      ? await gameApiClient.startDailyChallenge(spec.categorySlug)
      : await gameApiClient.startFreePlay(
          spec.categorySlug,
          resolveTarget(spec.target),
          spec.filter,
        );

  // Abandon only after the new game exists, so a failed start keeps the old one.
  // Never abandon a daily: that would spend the player's one attempt.
  if (previous?.spec.mode === "freeplay" && previous.gameId !== game.gameId) {
    gameApiClient.abandonGame(previous.gameId, "freeplay");
  }

  try {
    sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ gameId: game.gameId, spec } satisfies SavedGame),
    );
  } catch {
    /* storage full or unavailable — non-critical */
  }
  return game;
}

export function loadSavedGame(): SavedGame | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    // ponytail: pre-GameSpec entries (no `spec`) are dropped, not migrated —
    // sessionStorage is tab-scoped and in-progress dailies resume from their card.
    const mode = parsed?.spec?.mode;
    if (typeof parsed?.gameId === "string" && (mode === "daily" || mode === "freeplay")) {
      return parsed as SavedGame;
    }
  } catch {
    /* corrupted data */
  }
  return null;
}

export function clearSavedGame(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    /* ignore */
  }
}
