import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useGameLoop } from "@/hooks/useGameLoop";

// ── Hoisted mocks ──────────────────────────────────────────────────────────

const { mockApiFetch, mockAddToast, mockSetItem, mockGetItem, mockRemoveItem } =
  vi.hoisted(() => ({
    mockApiFetch: vi.fn(),
    mockAddToast: vi.fn(),
    mockSetItem: vi.fn(),
    mockGetItem: vi.fn(),
    mockRemoveItem: vi.fn(),
  }));

vi.mock("@/lib/api/client", () => ({
  apiFetch: mockApiFetch,
}));

vi.mock("@/context/ToastContext", () => ({
  useToast: () => ({ addToast: mockAddToast }),
}));

vi.mock("@/hooks/useAnimatedScore", () => ({
  useAnimatedScore: (score: number) => ({
    display: score,
    isAnimating: false,
    flashVersion: 0,
  }),
}));

// ── Helpers ────────────────────────────────────────────────────────────────

function mockGameResponse(overrides: Record<string, unknown> = {}) {
  return {
    gameId: "game-uuid-123",
    questionId: "q-uuid-456",
    currentScore: 501,
    questionText: "Appearances for Arsenal in the Premier League",
    turnCount: 0,
    entityType: "footballer",
    hints: { maxScoresLeft: 5, checkoutsLeft: 3 },
    status: "IN_PROGRESS",
    moves: [],
    ...overrides,
  };
}

function mockSubmitResponse(overrides: Record<string, unknown> = {}) {
  return {
    result: "VALID",
    scoreValue: 140,
    scoreAfter: 361,
    matchedAnswer: "Bukayo Saka",
    reason: null,
    isWin: false,
    gameState: { hints: { maxScoresLeft: 4, checkoutsLeft: 3 } },
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockGetItem.mockReturnValue(null); // no saved game by default

  // Mock sessionStorage
  Object.defineProperty(window, "sessionStorage", {
    value: {
      getItem: mockGetItem,
      setItem: mockSetItem,
      removeItem: mockRemoveItem,
    },
    writable: true,
  });
});

// ── Initial state ──────────────────────────────────────────────────────────

describe("useGameLoop — initial state", () => {
  it("starts with NOT_STARTED status", () => {
    const { result } = renderHook(() => useGameLoop());
    expect(result.current.gameStatus).toBe("NOT_STARTED");
  });

  it("starts with score 501", () => {
    const { result } = renderHook(() => useGameLoop());
    expect(result.current.score).toBe(501);
  });

  it("starts with no gameId", () => {
    const { result } = renderHook(() => useGameLoop());
    expect(result.current.gameId).toBeNull();
  });

  it("starts with default entityType 'footballer'", () => {
    const { result } = renderHook(() => useGameLoop());
    expect(result.current.entityType).toBe("footballer");
  });

  it("starts with default gameType 'freeplay'", () => {
    const { result } = renderHook(() => useGameLoop());
    expect(result.current.gameType).toBe("freeplay");
  });
});

// ── startNewGame (Free Play) ───────────────────────────────────────────────

describe("useGameLoop — startNewGame", () => {
  it("calls POST /api/freeplay/start with correct body", async () => {
    mockApiFetch.mockResolvedValue(
      new Response(JSON.stringify(mockGameResponse()), { status: 200 }),
    );

    const { result } = renderHook(() => useGameLoop());

    await act(async () => {
      await result.current.startNewGame("football", "Football", 301);
    });

    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/freeplay/start",
      expect.objectContaining({
        method: "POST",
        headers: { "Content-Type": "application/json" },
      }),
    );

    const [, init] = mockApiFetch.mock.calls[0];
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toEqual({
      categorySlug: "football",
      startingScore: 301,
      footballFilter: undefined,
    });
  });

  it("sets game state from response", async () => {
    mockApiFetch.mockResolvedValue(
      new Response(
        JSON.stringify(
          mockGameResponse({
            gameId: "abc-123",
            questionId: "Q1",
            currentScore: 251,
            questionText: "Test question?",
            entityType: "city",
          }),
        ),
        { status: 200 },
      ),
    );

    const { result } = renderHook(() => useGameLoop());

    await act(async () => {
      await result.current.startNewGame("geography", "Geography");
    });

    expect(result.current.gameId).toBe("abc-123");
    expect(result.current.questionId).toBe("Q1");
    expect(result.current.score).toBe(251);
    expect(result.current.question).toBe("Test question?");
    expect(result.current.entityType).toBe("city");
    expect(result.current.gameStatus).toBe("IN_PROGRESS");
    expect(result.current.gameType).toBe("freeplay");
  });

  it("saves game state to sessionStorage", async () => {
    mockApiFetch.mockResolvedValue(
      new Response(JSON.stringify(mockGameResponse()), { status: 200 }),
    );

    const { result } = renderHook(() => useGameLoop());

    await act(async () => {
      await result.current.startNewGame("football", "Football", 501);
    });

    expect(mockSetItem).toHaveBeenCalledWith(
      "activeGameState",
      expect.stringContaining("game-uuid-123"),
    );
  });

  it("shows error toast on failure", async () => {
    mockApiFetch.mockResolvedValue(
      new Response(JSON.stringify({ error: "No questions available" }), { status: 400 }),
    );

    const { result } = renderHook(() => useGameLoop());

    await act(async () => {
      await result.current.startNewGame("football", "Football");
    });

    expect(mockAddToast).toHaveBeenCalledWith("No questions available", "error");
    expect(result.current.gameStatus).toBe("NOT_STARTED");
  });
});

// ── startDailyChallenge ────────────────────────────────────────────────────

describe("useGameLoop — startDailyChallenge", () => {
  it("calls POST /api/daily-challenge/{slug}/start", async () => {
    mockApiFetch.mockResolvedValue(
      new Response(JSON.stringify(mockGameResponse()), { status: 200 }),
    );

    const { result } = renderHook(() => useGameLoop());

    await act(async () => {
      await result.current.startDailyChallenge("football", "Daily Football");
    });

    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/daily-challenge/football/start",
      expect.objectContaining({
        method: "POST",
        headers: { "Content-Type": "application/json" },
      }),
    );
  });

  it("sets gameType to daily-challenge", async () => {
    mockApiFetch.mockResolvedValue(
      new Response(JSON.stringify(mockGameResponse()), { status: 200 }),
    );

    const { result } = renderHook(() => useGameLoop());

    await act(async () => {
      await result.current.startDailyChallenge("football", "Daily Football");
    });

    expect(result.current.gameType).toBe("daily-challenge");
  });

  it("saves with gameType daily-challenge", async () => {
    mockApiFetch.mockResolvedValue(
      new Response(JSON.stringify(mockGameResponse()), { status: 200 }),
    );

    const { result } = renderHook(() => useGameLoop());

    await act(async () => {
      await result.current.startDailyChallenge("football", "Daily Football");
    });

    const saved = JSON.parse(mockSetItem.mock.calls[0][1]);
    expect(saved.gameType).toBe("daily-challenge");
  });
});

// ── submitAnswer ────────────────────────────────────────────────────────────

describe("useGameLoop — submitAnswer", () => {
  async function startGame() {
    mockApiFetch.mockResolvedValueOnce(
      new Response(JSON.stringify(mockGameResponse()), { status: 200 }),
    );

    const hook = renderHook(() => useGameLoop());

    await act(async () => {
      await hook.result.current.startNewGame("football", "Football");
    });

    mockApiFetch.mockClear();
    return hook;
  }

  it("calls POST /api/freeplay/games/{id}/submit with answer", async () => {
    const { result } = await startGame();

    mockApiFetch.mockResolvedValueOnce(
      new Response(JSON.stringify(mockSubmitResponse()), { status: 200 }),
    );

    await act(async () => {
      await result.current.submitAnswer("Bukayo Saka", "entity-1");
    });

    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/freeplay/games/game-uuid-123/submit",
      expect.objectContaining({ method: "POST" }),
    );

    const [, init] = mockApiFetch.mock.calls[0];
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toEqual({ answer: "Bukayo Saka", entityId: "entity-1" });
  });

  it("shows popup with VALID result on correct answer", async () => {
    const { result } = await startGame();

    mockApiFetch.mockResolvedValueOnce(
      new Response(
        JSON.stringify(
          mockSubmitResponse({ result: "VALID", scoreValue: 140 }),
        ),
        { status: 200 },
      ),
    );

    await act(async () => {
      await result.current.submitAnswer("Bukayo Saka");
    });

    expect(result.current.popup).toEqual({
      scoreValue: 140,
      result: "VALID",
      reason: undefined,
    });
    expect(result.current.isAnimating).toBe(true);
  });

  it("shows popup with BUST result on bust answer", async () => {
    const { result } = await startGame();

    mockApiFetch.mockResolvedValueOnce(
      new Response(
        JSON.stringify(
          mockSubmitResponse({
            result: "BUST",
            scoreValue: 0,
            scoreAfter: 501,
            reason: "Invalid darts score: 169",
          }),
        ),
        { status: 200 },
      ),
    );

    await act(async () => {
      await result.current.submitAnswer("Some Player");
    });

    expect(result.current.popup).toEqual({
      scoreValue: 0,
      result: "BUST",
      reason: "Invalid darts score: 169",
    });
  });

  it("does nothing when gameId is null", async () => {
    const { result } = renderHook(() => useGameLoop());

    await act(async () => {
      await result.current.submitAnswer("Test");
    });

    expect(mockApiFetch).not.toHaveBeenCalled();
  });

  it("does nothing when popup is already showing", async () => {
    const { result } = await startGame();

    // First submit — shows popup
    mockApiFetch.mockResolvedValueOnce(
      new Response(JSON.stringify(mockSubmitResponse()), { status: 200 }),
    );

    await act(async () => {
      await result.current.submitAnswer("First");
    });

    mockApiFetch.mockClear();

    // Second submit while popup is showing — should be ignored
    await act(async () => {
      await result.current.submitAnswer("Second");
    });

    expect(mockApiFetch).not.toHaveBeenCalled();
  });
});

// ── onPopupComplete — state transitions ────────────────────────────────────

describe("useGameLoop — onPopupComplete", () => {
  async function startAndSubmit(overrides: Record<string, unknown> = {}) {
    mockApiFetch.mockResolvedValueOnce(
      new Response(JSON.stringify(mockGameResponse()), { status: 200 }),
    );

    const hook = renderHook(() => useGameLoop());

    await act(async () => {
      await hook.result.current.startNewGame("football", "Football");
    });

    mockApiFetch.mockClear();

    mockApiFetch.mockResolvedValueOnce(
      new Response(JSON.stringify(mockSubmitResponse(overrides)), { status: 200 }),
    );

    await act(async () => {
      await hook.result.current.submitAnswer("Bukayo Saka");
    });

    return hook;
  }

  it("adds move to history on popup complete (VALID)", async () => {
    const { result } = await startAndSubmit({
      result: "VALID",
      scoreValue: 140,
      scoreAfter: 361,
      matchedAnswer: "Bukayo Saka",
    });

    await act(async () => {
      result.current.onPopupComplete();
    });

    expect(result.current.popup).toBeNull();
    expect(result.current.moves).toHaveLength(1);
    expect(result.current.moves[0]).toMatchObject({
      answer: "Bukayo Saka",
      result: "VALID",
      scoreBefore: 501,
      scoreAfter: 361,
      scoreValue: 140,
    });
    expect(result.current.score).toBe(361);
    expect(result.current.turnCount).toBe(1);
  });

  it("adds BUST move on popup complete", async () => {
    const { result } = await startAndSubmit({
      result: "BUST",
      scoreValue: 0,
      scoreAfter: 501,
      reason: "Score exceeds 180",
    });

    await act(async () => {
      result.current.onPopupComplete();
    });

    expect(result.current.moves[0].result).toBe("BUST");
    expect(result.current.moves[0].scoreValue).toBe(0);
    expect(result.current.moves[0].reason).toBe("Score exceeds 180");
    expect(result.current.score).toBe(501); // unchanged on bust
  });

  it("completes game on checkout (isWin)", async () => {
    const { result } = await startAndSubmit({
      result: "CHECKOUT",
      scoreValue: 10,
      scoreAfter: 0,
      isWin: true,
      gameState: { status: "COMPLETED" },
    });

    await act(async () => {
      result.current.onPopupComplete();
    });

    expect(result.current.gameStatus).toBe("COMPLETED");
    expect(result.current.isWin).toBe(true);
    expect(mockRemoveItem).toHaveBeenCalledWith("activeGameState");
  });

  it("updates hints from gameState in response", async () => {
    const { result } = await startAndSubmit({
      gameState: { hints: { maxScoresLeft: 2, checkoutsLeft: 1 } },
    });

    await act(async () => {
      result.current.onPopupComplete();
    });

    expect(result.current.hints).toEqual({ maxScoresLeft: 2, checkoutsLeft: 1 });
  });

  it("shows success toast on VALID", async () => {
    mockAddToast.mockClear();
    const { result } = await startAndSubmit({ result: "VALID", scoreValue: 140 });

    await act(async () => {
      result.current.onPopupComplete();
    });

    expect(mockAddToast).toHaveBeenCalledWith("Correct! -140", "success");
  });

  it("shows error toast on BUST with reason", async () => {
    mockAddToast.mockClear();
    const { result } = await startAndSubmit({
      result: "BUST",
      reason: "Invalid darts score: 169",
    });

    await act(async () => {
      result.current.onPopupComplete();
    });

    expect(mockAddToast).toHaveBeenCalledWith("BUST — Invalid darts score: 169", "error");
  });

  it("shows error toast on INVALID", async () => {
    mockAddToast.mockClear();
    const { result } = await startAndSubmit({
      result: "INVALID",
      reason: "Not a valid answer",
    });

    await act(async () => {
      result.current.onPopupComplete();
    });

    expect(mockAddToast).toHaveBeenCalledWith("Not a valid answer", "error");
  });
});

// ── exitGame ───────────────────────────────────────────────────────────────

describe("useGameLoop — exitGame", () => {
  it("calls abandon endpoint and resets state", async () => {
    mockApiFetch.mockResolvedValue(
      new Response(JSON.stringify(mockGameResponse()), { status: 200 }),
    );

    const { result } = renderHook(() => useGameLoop());

    // Start a game first
    await act(async () => {
      await result.current.startNewGame("football", "Football");
    });

    mockApiFetch.mockClear();

    // Exit
    await act(async () => {
      result.current.exitGame();
    });

    // Check abandon was called
    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/freeplay/games/game-uuid-123/abandon",
      expect.objectContaining({ method: "POST" }),
    );

    // Check state reset
    expect(result.current.gameStatus).toBe("NOT_STARTED");
    expect(result.current.gameId).toBeNull();
    expect(result.current.questionId).toBeNull();

    // Check sessionStorage cleared
    expect(mockRemoveItem).toHaveBeenCalledWith("activeGameState");
  });
});

// ── Session restore on mount ───────────────────────────────────────────────

describe("useGameLoop — session restore", () => {
  it("attempts restore when saved game exists in sessionStorage", async () => {
    mockGetItem.mockReturnValue(
      JSON.stringify({ gameId: "saved-game-1", label: "Football", gameType: "freeplay" }),
    );

    mockApiFetch.mockResolvedValue(
      new Response(
        JSON.stringify(
          mockGameResponse({
            gameId: "saved-game-1",
            currentScore: 320,
            turnCount: 2,
            moves: [
              { answer: "Player A", result: "VALID", scoreBefore: 501, scoreAfter: 380, scoreValue: 121 },
              { answer: "Player B", result: "VALID", scoreBefore: 380, scoreAfter: 320, scoreValue: 60 },
            ],
          }),
        ),
        { status: 200 },
      ),
    );

    const { result } = renderHook(() => useGameLoop());

    // Wait for the useEffect to fire
    await vi.waitFor(() => {
      expect(result.current.gameStatus).toBe("IN_PROGRESS");
    });

    expect(result.current.gameId).toBe("saved-game-1");
    expect(result.current.score).toBe(320);
    expect(result.current.turnCount).toBe(2);
    expect(result.current.moves).toHaveLength(2);
  });

  it("clears saved state and shows error toast when restore fails", async () => {
    mockGetItem.mockReturnValue(
      JSON.stringify({ gameId: "expired-game", label: "Film", gameType: "freeplay" }),
    );

    mockApiFetch.mockResolvedValue(
      new Response(JSON.stringify({ error: "Not found" }), { status: 404 }),
    );

    renderHook(() => useGameLoop());

    await vi.waitFor(() => {
      expect(mockRemoveItem).toHaveBeenCalledWith("activeGameState");
      expect(mockAddToast).toHaveBeenCalledWith(
        "Your previous game session has expired.",
        "error",
      );
    });
  });

  it("does not attempt restore when no saved game", async () => {
    mockGetItem.mockReturnValue(null);

    renderHook(() => useGameLoop());

    // Let useEffect fire
    await vi.waitFor(() => {
      // apiFetch should not be called for restore (no saved game)
    });

    expect(mockApiFetch).not.toHaveBeenCalled();
  });
});
