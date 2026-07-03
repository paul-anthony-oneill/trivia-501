"use client";

import { useState, useEffect, useRef } from "react";
import { useToast } from "@/context/ToastContext";
import { useAnimatedScore } from "@/hooks/useAnimatedScore";
import { gameApiClient } from "@/lib/api/GameApiClient";
import {
  saveGameState,
  loadSavedGameState,
  clearSavedGameState,
  getSavedLabel,
} from "@/hooks/useGamePersistence";
import {
  getDailyLock,
  setDailyLockInProgress,
  setDailyLockCompleted,
} from "@/lib/dailyLock";
import type { FootballFilter } from "@/lib/api/footballApi";
import type {
  Move,
  GameHints,
  GameStatus,
  GameType,
  GameStateResponse,
  SubmitAnswerResponse,
  PopupState,
  GameLoopState,
  GameLoopActions,
} from "@/hooks/useGameLoop.types";

// Re-export types so existing callers (page.tsx, MatchView.tsx) don't change
export type {
  Move,
  GameHints,
  GameStatus,
  GameType,
  GameStateResponse,
  SubmitAnswerResponse,
  PopupState,
  GameLoopState,
  GameLoopActions,
};

export { getSavedLabel } from "@/hooks/useGamePersistence";

/**
 * `useGameLoop` — thin coordinator that wires together:
 *
 * - {@code GameApiClient}              — typed API facade
 * - {@code useGamePersistence} module  — sessionStorage helpers
 * - {@code useAnimatedScore}    — score animation
 * - Toast context               — user feedback
 *
 * The public interface ({@code GameLoopState & GameLoopActions}) is unchanged
 * so callers don't need to update.
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
  const [isWin, setIsWin] = useState(false);
  const [moves, setMoves] = useState<Move[]>([]);
  const [entityType, setEntityType] = useState("footballer");
  const [hints, setHints] = useState<GameHints | null>(null);
  const [popup, setPopup] = useState<PopupState | null>(null);
  const [gameType, setGameType] = useState<GameType>("freeplay");
  const [gameId, setGameId] = useState<string | null>(null);
  const [questionId, setQuestionId] = useState<string | null>(null);
  const [currentCategorySlug, setCurrentCategorySlug] = useState<
    string | null
  >(null);

  const restoreAttempted = useRef(false);
  const pendingResultRef = useRef<{
    answer: string;
    result: SubmitAnswerResponse;
  } | null>(null);

  // ── Restore on mount ─────────────────────────────────────────────────────

  useEffect(() => {
    if (restoreAttempted.current) return;
    restoreAttempted.current = true;

    const saved = loadSavedGameState();
    if (!saved) return;

    const savedGameType = saved.gameType ?? "freeplay";
    setGameType(savedGameType);

    setGameStatus("RESTORING");

    gameApiClient
      .getGameState(saved.gameId, savedGameType)
      .then((game) => {
        setGameId(game.gameId);
        setQuestionId(game.questionId ?? null);
        setScore(game.currentScore);
        setQuestion(game.questionText);
        setTurnCount(game.turnCount ?? 0);
        setEntityType(game.entityType ?? "footballer");
        setHints(game.hints ?? null);
        const completed = game.status === "COMPLETED";
        setGameStatus(completed ? "COMPLETED" : "IN_PROGRESS");
        setIsWin(completed && game.isWin === true);

        if (savedGameType === "daily-challenge" && saved.categorySlug) {
          setCurrentCategorySlug(saved.categorySlug);
          if (game.status === "COMPLETED") {
            setDailyLockCompleted(saved.categorySlug, game.gameId);
          } else if ((game.turnCount ?? 0) > 0) {
            setDailyLockInProgress(saved.categorySlug, game.gameId);
          }
        }

        if (game.moves && Array.isArray(game.moves)) {
          const restoredMoves: Move[] = [...game.moves]
            .reverse()
            .map(
              (m) =>
                ({
                  answer: (m.answer as string) ?? "",
                  result: (m.result as string) ?? "UNKNOWN",
                  scoreBefore: (m.scoreBefore as number) ?? 0,
                  scoreAfter: (m.scoreAfter as number) ?? 0,
                  matchedAnswer: (m.matchedAnswer as string) ?? undefined,
                  scoreValue: (m.scoreValue as number) ?? undefined,
                  reason: (m.reason as string) ?? undefined,
                }) as Move,
            );
          setMoves(restoredMoves);
        }

        addToast("Game restored!", "success");
      })
      .catch(() => {
        clearSavedGameState();
        setGameStatus("NOT_STARTED");
        addToast("Your previous game session has expired.", "error");
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ── Actions ──────────────────────────────────────────────────────────────

  async function startNewGame(
    categorySlug: string,
    label: string,
    targetScore?: number,
    footballFilter?: FootballFilter,
  ) {
    setGameType("freeplay");
    if (gameId) gameApiClient.abandonGame(gameId, "freeplay");
    clearSavedGameState();
    try {
      const game = await gameApiClient.startFreePlay(
        categorySlug,
        targetScore,
        footballFilter,
      );
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
      addToast("Game started!", "success");
    } catch (err) {
      addToast((err as Error).message || "Error starting game", "error");
    }
  }

  async function startDailyChallenge(categorySlug: string, label: string) {
    setGameType("daily-challenge");
    if (gameId) gameApiClient.abandonGame(gameId, "freeplay");
    clearSavedGameState();
    try {
      const game = await gameApiClient.startDailyChallenge(categorySlug);
      setGameId(game.gameId);
      setQuestionId(game.questionId ?? null);
      setScore(game.currentScore);
      setQuestion(game.questionText);
      setTurnCount(game.turnCount ?? 0);
      if (game.moves && Array.isArray(game.moves)) {
        const mapped: Move[] = [...game.moves].reverse().map(
          (m) => ({
            answer: (m.answer as string) ?? "",
            result: (m.result as string) ?? "UNKNOWN",
            scoreBefore: (m.scoreBefore as number) ?? 0,
            scoreAfter: (m.scoreAfter as number) ?? 0,
            matchedAnswer: (m.matchedAnswer as string) ?? undefined,
            scoreValue: (m.scoreValue as number) ?? undefined,
            reason: (m.reason as string) ?? undefined,
          }) as Move,
        );
        setMoves(mapped);
      } else {
        setMoves([]);
      }
      setEntityType(game.entityType ?? "footballer");
      setHints(game.hints ?? null);
      setCurrentCategorySlug(categorySlug);
      setGameStatus("IN_PROGRESS");

      saveGameState(game.gameId, label, "daily-challenge", categorySlug);

      if ((game.turnCount ?? 0) > 0) {
        setDailyLockInProgress(categorySlug, game.gameId);
      }

      addToast(
        (game.turnCount ?? 0) > 0
          ? "Daily Challenge resumed!"
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
      const result = await gameApiClient.submitAnswer(
        gameId,
        answer,
        entityId ?? null,
        gameType,
      );

      // Stash the full response and show the popup — the popup calls
      // handlePopupComplete when it finishes.
      pendingResultRef.current = { answer: answer.trim(), result };
      setPopup({
        scoreValue: result.scoreValue ?? 0,
        result: result.result as PopupState["result"],
        reason: (result.reason as string) ?? undefined,
      });
    } catch (err) {
      addToast(
        err instanceof Error ? err.message : "Error validating answer",
        "error",
      );
    }
  }

  function handlePopupComplete() {
    const pending = pendingResultRef.current;
    if (!pending) return;

    const { answer, result: r } = pending;
    pendingResultRef.current = null;
    setPopup(null);

    const reasonText = r.reason ?? undefined;

    const newMove: Move = {
      answer,
      result: r.result ?? "UNKNOWN",
      scoreBefore: score,
      scoreAfter: r.scoreAfter ?? score,
      matchedAnswer: r.matchedAnswer ?? undefined,
      scoreValue: r.scoreValue ?? undefined,
      reason: reasonText,
    };

    setMoves((prev) => [newMove, ...prev]);
    setScore(r.scoreAfter ?? score);
    setTurnCount((prev) => prev + 1);
    setHints(r.gameState?.hints ?? null);

    if (r.result === "VALID") addToast(`Correct! -${r.scoreValue}`, "success");
    else if (r.result === "BUST")
      addToast(reasonText ? `BUST — ${reasonText}` : "BUST!", "error");
    else if (r.result === "INVALID")
      addToast(reasonText || "Not a valid answer — try again", "error");

    const gameCompleted = r.gameState?.status === "COMPLETED";
    const gameWasWon = r.isWin === true;

    if (gameCompleted) {
      setGameStatus("COMPLETED");
      setIsWin(gameWasWon);
      clearSavedGameState();
      if (gameType === "daily-challenge" && currentCategorySlug && gameId) {
        setDailyLockCompleted(currentCategorySlug, gameId);
      }
    } else if (
      gameType === "daily-challenge" &&
      currentCategorySlug &&
      gameId
    ) {
      const existing = getDailyLock(currentCategorySlug);
      if (!existing || existing.state === "in_progress") {
        setDailyLockInProgress(currentCategorySlug, gameId);
      }
    }
  }

  function exitGame() {
    if (gameId) gameApiClient.abandonGame(gameId, gameType);
    clearSavedGameState();
    setGameStatus("NOT_STARTED");
    setIsWin(false);
    setGameId(null);
    setQuestionId(null);
    setCurrentCategorySlug(null);
  }

  // ── Return ───────────────────────────────────────────────────────────────

  const isAnimating = popup !== null || scoreAnimating;

  return {
    score: displayScore,
    flashVersion,
    question,
    turnCount,
    gameStatus,
    isWin,
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
