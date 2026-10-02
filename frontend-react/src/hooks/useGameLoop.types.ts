"use client";

import type { Move, GameHints, GameType } from "@/lib/types/game";
import type { GameStatus, PopupState } from "@/lib/gameSession";

export type { Move, GameHints, GameType, GameStatus, PopupState };

// ─── Types for useGameLoop and its callers ───────────────────────────────────

export interface GameLoopState {
  /** Current game score (starts at 501, counts down to 0). */
  score: number;
  /** The active question text from the server. Empty string before game starts. */
  question: string;
  /** Number of turns taken so far. */
  turnCount: number;
  /** Overall lifecycle of the game session. */
  gameStatus: GameStatus;
  /** True when the game ended via CHECKOUT (win), false for bust-out/forfeit. */
  isWin: boolean;
  /** Move history, newest first. */
  moves: Move[];
  /** Entity type driving the autocomplete dropdown (e.g. "footballer", "city"). */
  entityType: string;
  /** In-game hint stats from the server; null until the first response. */
  hints: GameHints | null;
  /** True while a popup or scoreboard animation is playing — input should be disabled. */
  isAnimating: boolean;
  /** Version counter incremented on each score change; used for flash animation key. */
  flashVersion: number;
  /** Current popup shown over the game; null when hidden. */
  popup: PopupState | null;
  /** The active game type (freeplay or daily-challenge). */
  gameType: GameType;
  /** The Game Spec of the active game; null when no game is active. Drives the header and Play Again. */
  spec: import("@/lib/gameStart").GameSpec | null;
  /** The active game ID, null when no game is active. */
  gameId: string | null;
  /** The current question ID, used by debug tools to fetch all answers. */
  questionId: string | null;
}

export interface GameLoopActions {
  /** The current game type (freeplay or daily-challenge). */
  gameType: GameType;
  /** Start a game from a Game Spec (abandons the previous Free Play game). */
  startGame: (spec: import("@/lib/gameStart").GameSpec) => Promise<void>;
  /** Submit an answer for the current game turn. */
  submitAnswer: (answer: string, entityId?: string) => Promise<void>;
  /** Exit the current game and return to the lobby. */
  exitGame: () => void;
  /** Called by the popup component when its animation finishes. */
  onPopupComplete: () => void;
}
