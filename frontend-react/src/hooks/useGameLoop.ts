"use client";

import { useState, useEffect, useRef } from "react";
import { useToast } from "@/context/ToastContext";
import { useAnimatedScore } from "@/hooks/useAnimatedScore";
import { apiFetch } from "@/lib/api/client";
import {
  getDailyLock,
  setDailyLockInProgress,
  setDailyLockCompleted,
} from "@/lib/dailyLock";
import type { FootballFilter } from "@/lib/api/footballApi";

// ─── Types ────────────────────────────────────────────────────────────────────

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

// ─── sessionStorage helpers ────────────────────────────────────────────────────

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

/** Exposed so the page component can recover the saved label after a restore. */
export function getSavedLabel(): string | null {
  return loadSavedGameState()?.label ?? null;
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

export interface GameLoopState {
  /** Current game score (starts at 501, counts down to 0). */
  score: number;
  /** The active question text from the server. Empty string before game starts. */
  question: string;
  /** Number of turns taken so far. */
  turnCount: number;
  /** Overall lifecycle of the game session. */
  gameStatus: GameStatus;
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
    footballFilter?: FootballFilter,
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

export interface PopupState {
  scoreValue: number;
  result: "VALID" | "BUST" | "INVALID";
  reason?: string;
}

/**
 * `useGameLoop` — owns all game session state and the API calls that drive it.
 *
 * Session persistence: the active game ID and category label are saved to
 * `sessionStorage` so a browser refresh can restore the game in progress.
 * On mount we attempt to restore; if the server still has the game it resumes,
 * otherwise the saved state is cleared and the lobby is shown.
 */
export function useGameLoop(): GameLoopState & GameLoopActions {
  const { addToast } = useToast();

  const [score, setScore] = useState(501);
  const {
    display: displayScore,
    isAnimating: scoreAnimating,
    flashVersion,
  } = useAnimatedScore(score);
  const [question, setQuestion] = useState("");
  const [turnCount, setTurnCount] = useState(0);
  const [gameStatus, setGameStatus] = useState<GameStatus>("NOT_STARTED");
  const [moves, setMoves] = useState<Move[]>([]);
  const [entityType, setEntityType] = useState("footballer");
  const [hints, setHints] = useState<GameHints | null>(null);
  const [popup, setPopup] = useState<PopupState | null>(null);
  const [gameType, setGameType] = useState<GameType>("freeplay");

  // Internal game ID used to address subsequent move submissions
  const [gameId, setGameId] = useState<string | null>(null);
  // Question ID for debug tooling
  const [questionId, setQuestionId] = useState<string | null>(null);
  // Category slug for daily challenge lock writes
  const [currentCategorySlug, setCurrentCategorySlug] = useState<string | null>(null);

  /** Returns the API base path for the current game type. */
  function apiBase(): string {
    return gameType === "daily-challenge" ?
        "/api/daily-challenge"
      : "/api/freeplay";
  }

  // Tracks whether we've already attempted a restore on this mount
  const restoreAttempted = useRef(false);

  // Holds the full server response while the popup is playing
  const pendingResultRef = useRef<{
    answer: string;
    result: Record<string, unknown>;
  } | null>(null);

  // ── Restore on mount ───────────────────────────────────────────────────────

  useEffect(() => {
    if (restoreAttempted.current) return;
    restoreAttempted.current = true;

    const saved = loadSavedGameState();
    if (!saved) return;

    const savedGameType = saved.gameType ?? "freeplay";
    setGameType(savedGameType);
    const restoreBase =
      savedGameType === "daily-challenge" ?
        "/api/daily-challenge"
      : "/api/freeplay";

    setGameStatus("RESTORING");

    apiFetch(`${restoreBase}/games/${saved.gameId}`)
      .then(async (res) => {
        if (!res.ok) throw new Error("Game not found");
        return res.json();
      })
      .then((game) => {
        setGameId(game.gameId);
        setQuestionId(game.questionId ?? null);
        setScore(game.currentScore);
        setQuestion(game.questionText);
        setTurnCount(game.turnCount ?? 0);
        setEntityType(game.entityType ?? "footballer");
        setHints(game.hints ?? null);
        setGameStatus(
          game.status === "COMPLETED" ? "COMPLETED" : "IN_PROGRESS",
        );

        // Restore categorySlug for daily challenge lock writes
        if (savedGameType === "daily-challenge" && saved.categorySlug) {
          setCurrentCategorySlug(saved.categorySlug);
          // Re-sync the localStorage lock in case it was cleared
          if (game.status === "COMPLETED") {
            setDailyLockCompleted(saved.categorySlug, game.gameId);
          } else if ((game.turnCount ?? 0) > 0) {
            setDailyLockInProgress(saved.categorySlug, game.gameId);
          }
        }

        // Restore move history (server returns oldest-first; we keep newest-first)
        if (game.moves && Array.isArray(game.moves)) {
          const restoredMoves: Move[] = [...game.moves]
            .reverse()
            .map((m: Record<string, unknown>) => ({
              answer: (m.answer as string) ?? "",
              result: (m.result as string) ?? "UNKNOWN",
              scoreBefore: (m.scoreBefore as number) ?? 0,
              scoreAfter: (m.scoreAfter as number) ?? 0,
              matchedAnswer: (m.matchedAnswer as string) ?? undefined,
              scoreValue: (m.scoreValue as number) ?? undefined,
              reason: (m.reason as string) ?? undefined,
            }));
          setMoves(restoredMoves);
        }

        document.body.classList.remove("theme-home");
        document.body.classList.add("theme-teletext");
        addToast("Game restored!", "success");
      })
      .catch(() => {
        clearSavedGameState();
        setGameStatus("NOT_STARTED");
        addToast("Your previous game session has expired.", "error");
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ── Actions ─────────────────────────────────────────────────────────────────

  async function abandonCurrentGame() {
    if (!gameId) return;
    // Fire-and-forget: server is idempotent; don't block the UI on network errors
    apiFetch(`${apiBase()}/games/${gameId}/abandon`, { method: "POST" }).catch(
      () => {},
    );
  }

  async function startNewGame(
    categorySlug: string,
    label: string,
    targetScore?: number,
    footballFilter?: FootballFilter,
  ) {
    setGameType("freeplay");
    await abandonCurrentGame();
    clearSavedGameState();
    try {
      const res = await apiFetch(`/api/freeplay/start`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          categorySlug,
          startingScore: targetScore,
          footballFilter,
        }),
      });

      if (!res.ok) {
        const text = await res.text().catch(() => "");
        let msg = "Failed to start game";
        try {
          const parsed = JSON.parse(text);
          msg = parsed.error || parsed.message || text;
        } catch {
          msg = text || msg;
        }
        throw new Error(msg);
      }

      const game = await res.json();
      setGameId(game.gameId);
      setQuestionId(game.questionId ?? null);
      setScore(game.currentScore);
      setQuestion(game.questionText);
      setTurnCount(0);
      setMoves([]);
      setEntityType(game.entityType ?? "footballer");
      setHints(game.hints ?? null);
      setGameStatus("IN_PROGRESS");

      saveGameState(game.gameId, label, "freeplay");

      document.body.classList.remove("theme-home");
      document.body.classList.add("theme-teletext");

      addToast("Game started!", "success");
    } catch (err) {
      addToast((err as Error).message || "Error starting game", "error");
    }
  }

  async function startDailyChallenge(categorySlug: string, label: string) {
    setGameType("daily-challenge");
    await abandonCurrentGame();
    clearSavedGameState();
    try {
      const base = "/api/daily-challenge";
      const res = await apiFetch(
        `${base}/${encodeURIComponent(categorySlug)}/start`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
        },
      );

      if (!res.ok) {
        const text = await res.text().catch(() => "");
        let msg = "Failed to start daily challenge";
        try {
          const parsed = JSON.parse(text);
          msg = parsed.error || parsed.message || text;
        } catch {
          msg = text || msg;
        }
        throw new Error(msg);
      }

      const game = await res.json();
      setGameId(game.gameId);
      setQuestionId(game.questionId ?? null);
      setScore(game.currentScore);
      setQuestion(game.questionText);
      setTurnCount(game.turnCount ?? 0);
      setMoves([]);
      setEntityType(game.entityType ?? "footballer");
      setHints(game.hints ?? null);
      setCurrentCategorySlug(categorySlug);
      setGameStatus("IN_PROGRESS");

      saveGameState(game.gameId, label, "daily-challenge", categorySlug);

      // Re-sync lock if this was a resume (game already has darts thrown)
      if ((game.turnCount ?? 0) > 0) {
        setDailyLockInProgress(categorySlug, game.gameId);
      }

      document.body.classList.remove("theme-home");
      document.body.classList.add("theme-teletext");

      addToast(
        (game.turnCount ?? 0) > 0 ?
          "Daily Challenge resumed!"
        : "Daily Challenge started!",
        "success",
      );
    } catch (err) {
      addToast(
        (err as Error).message || "Error starting daily challenge",
        "error",
      );
    }
  }

  async function submitAnswer(answer: string, entityId?: string) {
    if (!gameId || !answer.trim() || popup) return;

    try {
      const res = await apiFetch(`${apiBase()}/games/${gameId}/submit`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          answer: answer.trim(),
          entityId: entityId ?? null,
        }),
      });

      if (!res.ok) {
        const text = await res.text().catch(() => "");
        let msg = "Validation failed";
        try {
          const parsed = JSON.parse(text);
          msg = parsed.error || parsed.message || text;
        } catch {
          msg = text || msg;
        }
        throw new Error(msg);
      }

      const result = await res.json();

      // Stash the full response and show the popup — the popup calls
      // handlePopupComplete when it finishes.
      pendingResultRef.current = { answer: answer.trim(), result };
      setPopup({
        scoreValue: result.scoreValue ?? 0,
        result: result.result as PopupState["result"],
        reason: (result.reason as string) ?? undefined,
      });
    } catch (err) {
      addToast(err instanceof Error ? err.message : "Error validating answer", "error");
    }
  }

  function handlePopupComplete() {
    const pending = pendingResultRef.current;
    if (!pending) return;

    const { answer, result: r } = pending;
    pendingResultRef.current = null;
    setPopup(null);

    const reasonText = (r.reason as string) ?? undefined;

    const newMove: Move = {
      answer,
      result: (r.result as string) ?? "UNKNOWN",
      scoreBefore: score,
      scoreAfter: (r.scoreAfter as number) ?? score,
      matchedAnswer: (r.matchedAnswer as string) ?? undefined,
      scoreValue: (r.scoreValue as number) ?? undefined,
      reason: reasonText,
    };

    setMoves((prev) => [newMove, ...prev]);
    setScore((r.scoreAfter as number) ?? score);
    setTurnCount((prev) => prev + 1);
    setHints(
      ((r.gameState as Record<string, unknown>)?.hints as GameHints | null) ??
        null,
    );

    if (r.result === "VALID") addToast(`Correct! -${r.scoreValue}`, "success");
    else if (r.result === "BUST")
      addToast(reasonText ? `BUST — ${reasonText}` : "BUST!", "error");
    else if (r.result === "INVALID")
      addToast(reasonText || "Not a valid answer — try again", "error");

    if (r.isWin) {
      setGameStatus("COMPLETED");
      clearSavedGameState();
      if (gameType === "daily-challenge" && currentCategorySlug && gameId) {
        setDailyLockCompleted(currentCategorySlug, gameId);
      }
    } else if (gameType === "daily-challenge" && currentCategorySlug && gameId) {
      // Lock on first dart (and keep lock current if localStorage was cleared mid-game)
      const existing = getDailyLock(currentCategorySlug);
      if (!existing || existing.state === "in_progress") {
        setDailyLockInProgress(currentCategorySlug, gameId);
      }
    }
  }

  function exitGame() {
    abandonCurrentGame();
    clearSavedGameState();
    setGameStatus("NOT_STARTED");
    setGameId(null);
    setQuestionId(null);
    setCurrentCategorySlug(null);
    document.body.classList.remove("theme-teletext");
    document.body.classList.add("theme-home");
  }

  // ── Return ───────────────────────────────────────────────────────────────────

  const isAnimating = popup !== null || scoreAnimating;

  return {
    score: displayScore,
    flashVersion,
    question,
    turnCount,
    gameStatus,
    moves,
    entityType,
    hints,
    isAnimating,
    popup,
    gameType,
    gameId,
    questionId,
    onPopupComplete: handlePopupComplete,
    startNewGame,
    startDailyChallenge,
    submitAnswer,
    exitGame,
  };
}
