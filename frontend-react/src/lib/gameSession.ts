import type { GameSpec } from "@/lib/gameStart";
import type {
  GameHints,
  GameStateResponse,
  GameStatus,
  Move,
  PopupState,
  SubmitAnswerResponse,
} from "@/hooks/useGameLoop.types";

/**
 * Game Session — the client's copy of one game, fed only by server snapshots.
 *
 * An answer is committed the moment the server responds (so persistence and
 * the Daily Lock never depend on an animation finishing) but stays unrevealed
 * until the popup completes: `visible()` shows the pre-answer values meanwhile.
 */

/** The fields the player sees change when an answer is revealed. */
interface Shown {
  score: number;
  turnCount: number;
  moves: Move[]; // newest first
  hints: GameHints | null;
  status: GameStatus;
  isWin: boolean;
}

export interface GameSession extends Shown {
  spec: GameSpec | null;
  gameId: string | null;
  questionId: string | null;
  question: string;
  entityType: string;
  /** A committed answer the popup hasn't revealed yet, with what was shown before it. */
  unrevealed: { popup: PopupState; before: Shown } | null;
}

export type SessionAction =
  | { type: "restoring"; spec: GameSpec }
  | { type: "adopted"; game: GameStateResponse; spec: GameSpec }
  | { type: "answered"; answer: string; result: SubmitAnswerResponse }
  | { type: "revealed" }
  | { type: "reset" };

export const initialSession: GameSession = {
  spec: null,
  gameId: null,
  questionId: null,
  question: "",
  entityType: "footballer",
  score: 501,
  turnCount: 0,
  moves: [],
  hints: null,
  status: "NOT_STARTED",
  isWin: false,
  unrevealed: null,
};

export function sessionReducer(s: GameSession, a: SessionAction): GameSession {
  switch (a.type) {
    case "restoring":
      return { ...initialSession, spec: a.spec, status: "RESTORING" };

    case "adopted": {
      const g = a.game;
      const completed = g.status === "COMPLETED";
      return {
        spec: a.spec,
        gameId: g.gameId,
        questionId: g.questionId ?? null,
        question: g.questionText,
        entityType: g.entityType ?? "footballer",
        score: g.currentScore,
        turnCount: g.turnCount ?? 0,
        moves: g.moves ? [...g.moves].reverse() : [],
        hints: g.hints ?? null,
        status: completed ? "COMPLETED" : "IN_PROGRESS",
        isWin: completed && g.isWin === true,
        unrevealed: null,
      };
    }

    case "answered": {
      const r = a.result;
      const move: Move = {
        answer: a.answer,
        result: r.result ?? "UNKNOWN",
        scoreBefore: s.score,
        scoreAfter: r.scoreAfter ?? s.score,
        matchedAnswer: r.matchedAnswer ?? undefined,
        scoreValue: r.scoreValue ?? undefined,
        reason: r.reason ?? undefined,
      };
      const completed = r.gameState?.status === "COMPLETED";
      return {
        ...s,
        score: move.scoreAfter,
        turnCount: s.turnCount + 1,
        moves: [move, ...s.moves],
        hints: r.gameState?.hints ?? null,
        status: completed ? "COMPLETED" : s.status,
        isWin: completed ? r.isWin === true : s.isWin,
        unrevealed: {
          popup: {
            scoreValue: r.scoreValue ?? 0,
            result: r.result as PopupState["result"],
            reason: r.reason ?? undefined,
          },
          before: pickShown(s),
        },
      };
    }

    case "revealed":
      return { ...s, unrevealed: null };

    case "reset":
      return initialSession;
  }
}

/** What the player should see: committed state, minus any unrevealed answer. */
export function visible(s: GameSession): GameSession {
  return s.unrevealed ? { ...s, ...s.unrevealed.before } : s;
}

function pickShown(s: Shown): Shown {
  return {
    score: s.score,
    turnCount: s.turnCount,
    moves: s.moves,
    hints: s.hints,
    status: s.status,
    isWin: s.isWin,
  };
}
