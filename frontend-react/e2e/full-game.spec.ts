import { test, expect } from "@playwright/test";

const API = "http://localhost:8080/api";

type AnswerDebug = {
  id: string;
  displayText: string;
  score: number;
  isValidDarts: boolean;
  isBust: boolean;
};

type Question = {
  id: string;
  categoryId: string;
  config: Record<string, unknown>;
};

type GameState = {
  gameId: string;
  matchId: string;
  questionId: string;
  currentScore: number;
  status: string;
  moves: unknown[];
};

type SubmitResult = {
  result: string;
  matchedAnswer: string;
  scoreValue: number;
  scoreBefore: number;
  scoreAfter: number;
  isWin: boolean;
  reason: string;
};

// ─── helpers ──────────────────────────────────────────────────────────────────

/** Valid darts scores: 1–180 excluding the known unattainable finishes. */
const INVALID_DARTS = new Set([163, 166, 169, 172, 173, 175, 176, 178, 179]);
function isValidDartsScore(n: number): boolean {
  return n >= 1 && n <= 180 && !INVALID_DARTS.has(n);
}

/**
 * Find a checkout path — a sequence of answer indices whose scores sum to
 * exactly the target (landing in [-10, 0] is a win). Greedy: take the largest
 * answer that fits, repeat. Falls back to first-fit if greedy doesn't finish.
 */
function findCheckoutPath(
  answers: AnswerDebug[],
  target: number,
): number[] | null {
  // Only use valid, non-bust answers
  const usable = answers
    .map((a, i) => ({ i, score: a.score }))
    .filter((a) => isValidDartsScore(a.score) && a.score <= 180);
  usable.sort((a, b) => b.score - a.score); // descending

  const greedy = (
    remaining: number,
    available: typeof usable,
    path: number[],
  ): number[] | null => {
    if (remaining >= -10 && remaining <= 0) return path;
    if (remaining < -10) return null;

    for (let j = 0; j < available.length; j++) {
      const a = available[j];
      if (a.score > remaining + 10) continue; // would bust (overshoot checkout zone)
      const rest = available.filter((_, k) => k !== j);
      const result = greedy(remaining - a.score, rest, [...path, a.i]);
      if (result) return result;
    }
    return null;
  };

  return greedy(target, usable, []);
}

// ─── tests ────────────────────────────────────────────────────────────────────

test("full game loop — checkout via API", async ({ request }) => {
  // 1. Discover a category
  const cats = await request.get(`${API}/categories`);
  expect(cats.ok()).toBeTruthy();
  const catList = (await cats.json()) as { slug: string }[];
  expect(catList.length).toBeGreaterThan(0);
  const slug = catList[0].slug;

  // 2. Discover a question
  const qs = await request.get(`${API}/categories/${slug}/questions`);
  expect(qs.ok()).toBeTruthy();
  const questions = (await qs.json()) as Question[];
  expect(questions.length).toBeGreaterThan(0);
  const questionId = questions[0].id;

  // 3. Start game with known question + starting score
  const startingScore = 201;
  const start = await request.post(`${API}/freeplay/start`, {
    data: { questionId, startingScore },
  });
  expect(start.ok(), "start game failed").toBeTruthy();
  const gameState = (await start.json()) as GameState;
  expect(gameState.currentScore).toBe(startingScore);
  expect(gameState.status).toBe("IN_PROGRESS");

  const gameId = gameState.gameId;

  // 4. Get the answer key
  const ans = await request.get(`${API}/freeplay/games/${gameId}/answers`);
  expect(ans.ok(), "answers endpoint failed").toBeTruthy();
  const answers = (await ans.json()) as AnswerDebug[];

  // 5. Find a checkout path
  const path = findCheckoutPath(answers, startingScore);
  expect(path, "no checkout path found").not.toBeNull();
  expect(path!.length).toBeGreaterThan(0);

  // 6. Play through the path
  let currentScore = startingScore;
  for (let i = 0; i < path!.length; i++) {
    const answer = answers[path![i]];
    const submit = await request.post(`${API}/freeplay/games/${gameId}/submit`, {
      data: { answer: answer.displayText },
    });
    expect(submit.ok(), `submit failed for "${answer.displayText}"`).toBeTruthy();
    const result = (await submit.json()) as SubmitResult;

    expect(result.matchedAnswer).toBeDefined();
    expect(result.scoreBefore).toBe(currentScore);
    expect(result.scoreAfter).toBe(currentScore - answer.score);
    currentScore = result.scoreAfter;

    const isLast = i === path!.length - 1;
    if (isLast) {
      expect(result.result).toBe("CHECKOUT");
      expect(result.isWin).toBe(true);
      expect(result.scoreAfter).toBeGreaterThanOrEqual(-10);
      expect(result.scoreAfter).toBeLessThanOrEqual(0);
    } else {
      expect(result.result).toBe("VALID");
    }
  }

  // 7. Verify final game state
  const final = await request.get(`${API}/freeplay/games/${gameId}`);
  expect(final.ok()).toBeTruthy();
  const finalState = (await final.json()) as GameState;
  expect(finalState.status).toBe("COMPLETED");
});
