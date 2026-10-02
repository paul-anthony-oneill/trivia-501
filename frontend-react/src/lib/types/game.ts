// ─── Game API DTOs: the shapes the backend sends ───────────────────────────────

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

export type GameType = "freeplay" | "daily-challenge";

/** Matches backend {@code com.trivia501.dto.GameStateResponse}. */
export interface GameStateResponse {
  gameId: string;
  matchId: string;
  questionId: string;
  questionText: string;
  currentScore: number;
  turnCount: number;
  status: "IN_PROGRESS" | "COMPLETED" | "ABANDONED";
  isWin?: boolean;
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

/** Matches backend {@code DailyChallengeStatusResponse.CategoryChallenge}. */
export interface DailyStatus {
  categorySlug: string;
  categoryName: string;
  startingScore: number;
  questionText: string;
  hasChallenge: boolean;
}

/** Debug only: one row of the "all answers" endpoint. */
export interface AnswerItem {
  id: string;
  displayText: string;
  score: number;
  isValidDarts: boolean;
  isBust: boolean;
}
