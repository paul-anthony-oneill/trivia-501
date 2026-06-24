"use client";

import type { GameType } from "@/hooks/useGameLoop.types";

// ─── sessionStorage helpers for game session persistence ───────────────────

const GAME_STORAGE_KEY = "activeGameState";

interface SavedGameState {
  gameId: string;
  label: string;
  gameType: GameType;
  categorySlug?: string;
}

function saveGameState(
  gameId: string,
  label: string,
  gameType: GameType,
  categorySlug?: string,
) {
  try {
    sessionStorage.setItem(
      GAME_STORAGE_KEY,
      JSON.stringify({ gameId, label, gameType, categorySlug }),
    );
  } catch {
    /* storage full or unavailable — non-critical */
  }
}

function loadSavedGameState(): SavedGameState | null {
  try {
    const raw = sessionStorage.getItem(GAME_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (parsed.gameId && typeof parsed.gameId === "string") {
      return {
        gameId: parsed.gameId,
        label: parsed.label ?? "",
        gameType:
          parsed.gameType === "daily-challenge" ?
            "daily-challenge"
          : "freeplay",
        categorySlug: parsed.categorySlug ?? undefined,
      };
    }
  } catch {
    /* corrupted data */
  }
  return null;
}

function clearSavedGameState() {
  try {
    sessionStorage.removeItem(GAME_STORAGE_KEY);
  } catch {
    /* ignore */
  }
}

export { saveGameState, loadSavedGameState, clearSavedGameState };

/** Exposed so the page component can recover the saved label after a restore. */
export function getSavedLabel(): string | null {
  return loadSavedGameState()?.label ?? null;
}
