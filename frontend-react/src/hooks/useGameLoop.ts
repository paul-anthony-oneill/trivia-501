"use client";

import { useReducer, useEffect, useRef } from "react";
import { useToast } from "@/context/ToastContext";
import { useAnimatedScore } from "@/hooks/useAnimatedScore";
import { gameApiClient } from "@/lib/api/GameApiClient";
import {
  startGame as requestStart,
  loadSavedGame,
  clearSavedGame,
  type GameSpec,
} from "@/lib/gameStart";
import { recordDailyProgress } from "@/lib/dailyLock";
import { sessionReducer, initialSession, visible } from "@/lib/gameSession";
import type {
  Move,
  GameHints,
  GameStatus,
  GameType,
  PopupState,
  GameLoopState,
  GameLoopActions,
} from "@/hooks/useGameLoop.types";
import type { GameStateResponse, SubmitAnswerResponse } from "@/lib/types/game";

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
 * Game-start module (`lib/gameStart.ts`); state lives in the pure Game Session
 * reducer (`lib/gameSession.ts`). This hook runs the side effects: API calls,
 * persistence, the Daily Lock and toasts.
 */
export function useGameLoop(): GameLoopState & GameLoopActions {
  const { addToast } = useToast();
  const [session, dispatch] = useReducer(sessionReducer, initialSession);
  const view = visible(session);
  const { spec, gameId } = session;
  const gameType: GameType = spec?.mode === "daily" ? "daily-challenge" : "freeplay";

  const {
    display: displayScore,
    isAnimating: scoreAnimating,
    flashVersion,
  } = useAnimatedScore(view.score);

  const restoreAttempted = useRef(false);

  // ── Adopt a server snapshot (start or restore) ───────────────────────────

  function adopt(game: GameStateResponse, gameSpec: GameSpec) {
    dispatch({ type: "adopted", game, spec: gameSpec });

    const turns = game.turnCount ?? 0;
    const completed = game.status === "COMPLETED";
    if (gameSpec.mode === "daily") {
      recordDailyProgress(gameSpec.categorySlug, game.gameId, { completed, turnCount: turns });
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

    dispatch({ type: "restoring", spec: saved.spec });

    gameApiClient
      .getGameState(
        saved.gameId,
        saved.spec.mode === "daily" ? "daily-challenge" : "freeplay",
      )
      .then((game) => {
        if (game.status === "ABANDONED") {
          clearSavedGame();
          dispatch({ type: "reset" });
          addToast("Your previous game expired.", "info");
          return;
        }
        adopt(game, saved.spec);
      })
      .catch(() => {
        clearSavedGame();
        dispatch({ type: "reset" });
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
    if (!gameId || !answer.trim() || session.unrevealed) return;

    try {
      const result = await gameApiClient.submitAnswer(
        gameId,
        answer,
        entityId ?? null,
        gameType,
      );

      // Commit now; the popup only decides when the player sees it.
      dispatch({ type: "answered", answer: answer.trim(), result });

      const completed = result.gameState?.status === "COMPLETED";
      if (completed) clearSavedGame();
      if (spec?.mode === "daily") {
        recordDailyProgress(spec.categorySlug, gameId, {
          completed,
          turnCount: session.turnCount + 1,
        });
      }
    } catch (err) {
      addToast(
        err instanceof Error ? err.message : "Error validating answer",
        "error",
      );
    }
  }

  function handlePopupComplete() {
    if (!session.unrevealed) return;
    dispatch({ type: "revealed" });

    const { result, scoreValue, reason } = session.unrevealed.popup;
    if (result === "VALID") addToast(`Correct! -${scoreValue}`, "success");
    else if (result === "BUST")
      addToast(reason ? `BUST — ${reason}` : "BUST!", "error");
    else if (result === "INVALID")
      addToast(reason || "Not a valid answer — try again", "error");
  }

  function exitGame() {
    if (gameId) gameApiClient.abandonGame(gameId, gameType);
    clearSavedGame();
    dispatch({ type: "reset" });
  }

  // ── Return ───────────────────────────────────────────────────────────────

  const popup = session.unrevealed?.popup ?? null;

  return {
    score: displayScore,
    flashVersion,
    question: view.question,
    turnCount: view.turnCount,
    gameStatus: view.status,
    isWin: view.isWin,
    moves: view.moves,
    entityType: view.entityType,
    hints: view.hints,
    isAnimating: popup !== null || scoreAnimating,
    popup,
    gameType,
    spec,
    gameId,
    questionId: session.questionId,
    onPopupComplete: handlePopupComplete,
    startGame,
    submitAnswer,
    exitGame,
  };
}
