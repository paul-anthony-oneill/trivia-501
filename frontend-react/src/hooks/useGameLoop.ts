"use client";

import { useState, useEffect, useRef } from "react";
import { useToast } from "@/context/ToastContext";
import { useAnimatedScore } from "@/hooks/useAnimatedScore";
import { gameApiClient } from "@/lib/api/GameApiClient";
import {
  startGame as requestStart,
  loadSavedGame,
  clearSavedGame,
  type GameSpec,
} from "@/lib/gameStart";
import {
  getDailyLock,
  setDailyLockInProgress,
  setDailyLockCompleted,
} from "@/lib/dailyLock";
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

/**
 * `useGameLoop` — owns the game session on `/`. Games are started by the
 * Game-start module (`lib/gameStart.ts`); every server snapshot, whether from
 * a start or a restore, enters hook state through `adopt`.
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
  const [spec, setSpec] = useState<GameSpec | null>(null);
  const [gameId, setGameId] = useState<string | null>(null);
  const [questionId, setQuestionId] = useState<string | null>(null);

  const gameType: GameType = spec?.mode === "daily" ? "daily-challenge" : "freeplay";

  const restoreAttempted = useRef(false);
  const pendingResultRef = useRef<{
    answer: string;
    result: SubmitAnswerResponse;
  } | null>(null);

  // ── Adopt a server snapshot (start or restore) ───────────────────────────

  function adopt(game: GameStateResponse, gameSpec: GameSpec) {
    const turns = game.turnCount ?? 0;
    const completed = game.status === "COMPLETED";

    setSpec(gameSpec);
    setGameId(game.gameId);
    setQuestionId(game.questionId ?? null);
    setScore(game.currentScore);
    setQuestion(game.questionText);
    setTurnCount(turns);
    setMoves(game.moves ? [...game.moves].reverse() : []);
    setEntityType(game.entityType ?? "footballer");
    setHints(game.hints ?? null);
    setGameStatus(completed ? "COMPLETED" : "IN_PROGRESS");
    setIsWin(completed && game.isWin === true);

    if (gameSpec.mode === "daily") {
      if (completed) setDailyLockCompleted(gameSpec.categorySlug, game.gameId);
      else if (turns > 0) setDailyLockInProgress(gameSpec.categorySlug, game.gameId);
    }

    if (completed) return;
    const what = gameSpec.mode === "daily" ? "Daily Challenge" : "Game";
    addToast(turns > 0 ? `${what} resumed!` : `${what} started!`, "success");
  }

  // ── Restore on mount ─────────────────────────────────────────────────────

  useEffect(() => {
    if (restoreAttempted.current) return;
    restoreAttempted.current = true;

    const saved = loadSavedGame();
    if (!saved) return;

    setSpec(saved.spec);
    setGameStatus("RESTORING");

    gameApiClient
      .getGameState(
        saved.gameId,
        saved.spec.mode === "daily" ? "daily-challenge" : "freeplay",
      )
      .then((game) => {
        if (game.status === "ABANDONED") {
          clearSavedGame();
          setGameStatus("NOT_STARTED");
          addToast("Your previous game expired.", "info");
          return;
        }
        adopt(game, saved.spec);
      })
      .catch(() => {
        clearSavedGame();
        setGameStatus("NOT_STARTED");
        addToast("Your previous game session has expired.", "error");
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ── Actions ──────────────────────────────────────────────────────────────

  async function startGame(gameSpec: GameSpec) {
    try {
      adopt(await requestStart(gameSpec), gameSpec);
    } catch (err) {
      addToast((err as Error).message || "Error starting game", "error");
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
      clearSavedGame();
      if (spec?.mode === "daily" && gameId) {
        setDailyLockCompleted(spec.categorySlug, gameId);
      }
    } else if (spec?.mode === "daily" && gameId) {
      const existing = getDailyLock(spec.categorySlug);
      if (!existing || existing.state === "in_progress") {
        setDailyLockInProgress(spec.categorySlug, gameId);
      }
    }
  }

  function exitGame() {
    if (gameId) gameApiClient.abandonGame(gameId, gameType);
    clearSavedGame();
    setGameStatus("NOT_STARTED");
    setIsWin(false);
    setGameId(null);
    setQuestionId(null);
    setSpec(null);
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
    spec,
    gameId,
    questionId,
    onPopupComplete: handlePopupComplete,
    startGame,
    submitAnswer,
    exitGame,
  };
}
