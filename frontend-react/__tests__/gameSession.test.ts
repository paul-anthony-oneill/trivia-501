import { describe, it, expect } from "vitest";
import { sessionReducer, initialSession, visible, type GameSession } from "@/lib/gameSession";
import type { GameStateResponse, SubmitAnswerResponse } from "@/lib/types/game";
import type { GameSpec } from "@/lib/gameStart";

const spec: GameSpec = { mode: "daily", categorySlug: "football", breadcrumb: ["Football"] };

const game: GameStateResponse = {
  gameId: "g1",
  matchId: "m1",
  questionId: "q1",
  questionText: "Appearances for Arsenal",
  currentScore: 141,
  turnCount: 0,
  status: "IN_PROGRESS",
};

function answer(r: Partial<SubmitAnswerResponse>): SubmitAnswerResponse {
  return { result: "VALID", gameState: game, ...r };
}

function started(): GameSession {
  return sessionReducer(initialSession, { type: "adopted", game, spec });
}

describe("sessionReducer", () => {
  it("adopts a snapshot, reversing moves to newest-first", () => {
    const s = sessionReducer(initialSession, {
      type: "adopted",
      spec,
      game: {
        ...game,
        turnCount: 2,
        moves: [
          { answer: "a", result: "VALID", scoreBefore: 141, scoreAfter: 100 },
          { answer: "b", result: "BUST", scoreBefore: 100, scoreAfter: 100 },
        ],
      },
    });
    expect(s.status).toBe("IN_PROGRESS");
    expect(s.moves.map((m) => m.answer)).toEqual(["b", "a"]);
    expect(s.unrevealed).toBeNull();
  });

  it("commits an answer immediately but keeps it hidden until revealed", () => {
    const s = sessionReducer(started(), {
      type: "answered",
      answer: "Saka",
      result: answer({
        result: "CHECKOUT",
        scoreValue: 141,
        scoreAfter: 0,
        isWin: true,
        gameState: { ...game, status: "COMPLETED" },
      }),
    });

    // Committed
    expect(s.status).toBe("COMPLETED");
    expect(s.isWin).toBe(true);
    expect(s.score).toBe(0);
    expect(s.moves[0]).toMatchObject({ answer: "Saka", scoreBefore: 141, scoreAfter: 0 });

    // Not yet shown
    const v = visible(s);
    expect(v.status).toBe("IN_PROGRESS");
    expect(v.isWin).toBe(false);
    expect(v.score).toBe(141);
    expect(v.moves).toHaveLength(0);
    expect(v.turnCount).toBe(0);
    expect(s.unrevealed?.popup).toMatchObject({ result: "CHECKOUT", scoreValue: 141 });

    // Revealed
    const r = visible(sessionReducer(s, { type: "revealed" }));
    expect(r.status).toBe("COMPLETED");
    expect(r.score).toBe(0);
    expect(r.moves).toHaveLength(1);
  });

  it("keeps the score on a bust", () => {
    const s = sessionReducer(started(), {
      type: "answered",
      answer: "x",
      result: answer({ result: "BUST", scoreValue: 0, scoreAfter: 141 }),
    });
    expect(s.score).toBe(141);
    expect(s.turnCount).toBe(1);
    expect(s.status).toBe("IN_PROGRESS");
  });

  it("resets to the initial session", () => {
    expect(sessionReducer(started(), { type: "reset" })).toBe(initialSession);
  });
});
