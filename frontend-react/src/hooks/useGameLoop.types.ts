"use client";

// ─── Types shared by useGameLoop, GameApiClient, and callers ─────────────────

export interface Move {
  answer: string;
  result: string;
  scoreBefore: number;
  scoreAfter: number;
  matchedAnswer?: string;
  scoreValue?: number;
  reason?: string;
}

export interface GameHints {
  /** Remaining unused answers worth exactly 180 points. Shown while score > 180. */
  maxScoresLeft: number;
  /** Remaining unused answers that would win the game in one move. Shown while score ≤ 180. */
  checkoutsLeft: number;
}

export type GameStatus =
  | "NOT_STARTED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "RESTORING";
export type GameType = "freeplay" | "daily-challenge";

/** Matches backend {@code com.trivia501.dto.GameStateResponse}. */
export interface GameStateResponse {
  gameId: string;
  matchId: string;
  questionId: string;
  questionText: string;
  currentScore: number;
  turnCount: number;
  status: "IN_PROGRESS" | "COMPLETED";
  isWin?: boolean;
  turnTimerSeconds?: number;
  entityType?: string;
  hints?: GameHints;
  moves?: Move[];
}

/** Matches backend {@code com.trivia501.dto.SubmitAnswerResponse}. */
export interface SubmitAnswerResponse {
  result: string;
  matchedAnswer?: string;
  scoreValue?: number;
  scoreBefore?: number;
  scoreAfter?: number;
  reason?: string;
  isWin?: boolean;
  gameState: GameStateResponse;
}

export interface PopupState {
  scoreValue: number;
  result: "VALID" | "BUST" | "INVALID";
  reason?: string;
}

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
  /** The active game ID, null when no game is active. */
  gameId: string | null;
  /** The current question ID, used by debug tools to fetch all answers. */
  questionId: string | null;
}

export interface GameLoopActions {
  /** The current game type (freeplay or daily-challenge). */
  gameType: GameType;
  /** Start a new Free Play game for the given category slug. */
  startNewGame: (
    categorySlug: string,
    label: string,
    targetScore?: number,
    footballFilter?: import("@/lib/api/footballApi").FootballFilter,
  ) => Promise<void>;
  /** Start a daily challenge game for the given category slug. */
  startDailyChallenge: (categorySlug: string, label: string) => Promise<void>;
  /** Submit an answer for the current game turn. */
  submitAnswer: (answer: string, entityId?: string) => Promise<void>;
  /** Exit the current game and return to the lobby. */
  exitGame: () => void;
  /** Called by the popup component when its animation finishes. */
  onPopupComplete: () => void;
}
