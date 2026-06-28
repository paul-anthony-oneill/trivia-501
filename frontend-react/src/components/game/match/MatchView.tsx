"use client";

import React, { useState } from "react";
import EntitySearch from "../EntitySearch";
import WinOverlay from "./WinOverlay";
import LossOverlay from "./LossOverlay";
import MoveHistory from "./MoveHistory";
import DebugPanel from "../DebugPanel";
import Link from "next/link";
import LoginButton from "@/components/auth/LoginButton";
import ThemeToggle from "@/components/ui/ThemeToggle";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import { useAuth } from "@/context/AuthContext";
import type { Move, GameHints } from "@/hooks/useGameLoop";

interface StagedAnswer {
  name: string;
  entityId?: string;
}

interface MatchViewProps {
  score: number;
  question: string;
  turnCount: number;
  moves: Move[];
  onExit: () => void;
  onSubmitAnswer: (answer: string, entityId?: string) => void;
  onPlayAgain: () => void;
  categoryName: string;
  categorySub: string;
  /** Entity type passed to autocomplete (e.g. "footballer", "city"). */
  entityType?: string;
  /** True when the game ended via CHECKOUT (win), false for bust-out/forfeit. */
  isWin?: boolean;
  /** True when the game has ended for any reason (win, bust-out, forfeit). */
  isGameOver?: boolean;
  /** In-game hint stats from the server. Null until the first response arrives. */
  hints?: GameHints | null;
  /** Disables the answer input while an animation is playing. */
  disabled?: boolean;
  /** Version counter for the score value; used as a React key to trigger flash animation. */
  flashVersion?: number;
  /** Called when the player clicks "SHARE RESULT" in the win overlay. Daily-challenge only. */
  onShare?: () => void;
  /** Share button state: idle → sharing → copied. */
  shareState?: "idle" | "sharing" | "copied";
  /** Current game ID, used by DebugPanel to fetch all answers. */
  gameId?: string | null;
  /** Current game type */
  gameType?: "freeplay" | "daily-challenge";
  /** Last API error message; null after a successful request. */
  lastError?: string | null;
}

export default function MatchView({
  score,
  question,
  turnCount,
  moves,
  onExit,
  onSubmitAnswer,
  onPlayAgain,
  categoryName,
  categorySub,
  entityType = "footballer",
  isWin = false,
  isGameOver = false,
  hints = null,
  disabled = false,
  flashVersion = 0,
  onShare,
  shareState = "idle",
  gameId = null,
  gameType = "freeplay",
  lastError = null,
}: MatchViewProps) {
  const { user } = useAuth();
  const isAdmin = user?.app_metadata?.role === "admin";
  const [staged, setStaged] = useState<StagedAnswer | null>(null);
  const [showExitConfirm, setShowExitConfirm] = useState(false);
  const [dismissedError, setDismissedError] = useState<string | null>(null);

  function handleStage(name: string, entityId?: string) {
    setStaged({ name, entityId });
  }

  function handleThrowDart() {
    if (!staged) return;
    onSubmitAnswer(staged.name, staged.entityId);
    setStaged(null);
  }

  // Newest move first. Used for the flash colour, bust shake and checkout track.
  const lastMove = moves[0] ?? null;
  const lastWasBust = lastMove?.result === "BUST";
  const flashColor =
    lastMove?.result === "VALID" ? "var(--ok)"
    : lastWasBust ? "var(--danger)"
    : "var(--ink)";

  // The starting score is the score before the oldest move.
  const startingScore =
    moves.length > 0 ? moves[moves.length - 1].scoreBefore : score;
  const progress =
    startingScore > 0 ?
      Math.min(
        1,
        Math.max(0, (startingScore - Math.max(score, 0)) / startingScore),
      )
    : 0;

  return (
    <div
      className="min-h-screen flex flex-col bg-bg text-ink font-sans relative"
      onKeyDown={(e) => {
        if (e.key === "Enter" && staged && !disabled) {
          handleThrowDart();
        }
      }}
    >
      {/* Top bar */}
      <header className="flex items-center justify-between gap-3 px-4 md:px-8 py-3.5 border-b border-line">
        <button
          onClick={() => setShowExitConfirm(true)}
          className="btn-ghost px-3.5 py-2 flex-shrink-0"
        >
          ← Exit
        </button>
        <Link
          href="/"
          className="bullseye hover:scale-125 transition-transform"
          aria-label="Back to home"
          title="Home"
        />
        <div className="text-center min-w-0">
          <div className="font-display font-bold text-base md:text-lg leading-tight truncate">
            {categoryName}
          </div>
          <div className="kicker truncate">{categorySub}</div>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0">
          <ThemeToggle />
          <div className="hidden md:block">
            <LoginButton />
          </div>
        </div>
      </header>

      {/* Network / API error banner — persistent until next success or manual dismiss */}
      {lastError && lastError !== dismissedError && (
        <div className="animate-slide-down mx-4 md:mx-8 mt-3 flex items-center gap-3 bg-danger-soft border border-danger/30 rounded-md px-4 py-2.5 text-sm">
          <span className="font-mono text-[10px] tracking-[0.15em] uppercase text-danger shrink-0">
            Connection issue
          </span>
          <span className="flex-1 text-muted text-xs truncate">
            {lastError}
          </span>
          <button
            onClick={() => setDismissedError(lastError)}
            className="text-muted hover:text-ink text-sm leading-none transition-colors p-1"
            aria-label="Dismiss error"
          >
            ✕
          </button>
        </div>
      )}

      {/* Main game area — bottom padding on mobile clears the sticky answer bar */}
      <main className="flex-1 grid grid-cols-1 lg:grid-cols-[1fr_380px] gap-5 md:gap-8 p-4 md:p-8 pb-52 lg:pb-8 max-w-7xl w-full mx-auto">
        <div className="flex flex-col gap-5 md:gap-7 min-w-0">
          {/* Score hero */}
          <section
            className="bg-surface border border-line rounded-md p-5 md:p-7 relative overflow-hidden"
            aria-label="Score"
          >
            <div className="kicker">Points remaining</div>

            <div
              key={lastWasBust ? `shake-${moves.length}` : "steady"}
              className={lastWasBust ? "animate-shake" : ""}
            >
              <div
                className="display-num"
                style={{ fontSize: "clamp(96px, 16vw, 170px)" }}
              >
                <span
                  key={flashVersion}
                  className="animate-score-pop inline-block"
                  style={{ "--flash": flashColor } as React.CSSProperties}
                >
                  {score}
                </span>
              </div>
            </div>

            {/* Checkout track — how far through the game you are */}
            <div className="mt-4" aria-hidden="true">
              <div className="h-1.5 rounded-full bg-surface-2 overflow-hidden">
                <div
                  className="h-full rounded-full bg-ok transition-all duration-700 ease-out"
                  style={{ width: `${progress * 100}%` }}
                />
              </div>
              <div className="flex justify-between mt-1.5">
                <span className="font-mono text-[10px] text-muted tabular-nums">
                  {startingScore}
                </span>
                <span className="font-mono text-[10px] text-gold tabular-nums">
                  ◎ 0
                </span>
              </div>
            </div>

            {/* Last score + hints */}
            <div className="mt-4 pt-4 border-t border-line flex flex-wrap items-center gap-x-6 gap-y-2">
              <div className="flex items-baseline gap-2">
                <span className="kicker">Last score</span>
                <span
                  className={`font-display font-bold text-lg tabular-nums ${
                    lastMove ?
                      lastWasBust ? "text-danger"
                      : "text-ok"
                    : "text-muted"
                  }`}
                >
                  {lastMove ?
                    lastMove.result === "INVALID" ?
                      "—"
                    : lastMove.scoreValue
                  : "—"}
                </span>
              </div>

              {hints !== null && (
                <>
                  <div
                    className={`flex items-baseline gap-2 transition-opacity ${score > 180 ? "" : "opacity-30"}`}
                  >
                    <span className="kicker">180s left</span>
                    <span className="font-display font-bold text-lg tabular-nums">
                      {hints.maxScoresLeft}
                    </span>
                  </div>
                  <div
                    className={`flex items-baseline gap-2 transition-opacity ${score <= 180 ? "" : "opacity-30"}`}
                  >
                    <span className="kicker">Checkouts</span>
                    <span
                      className={`font-display font-bold text-lg tabular-nums ${
                        score <= 180 && hints.checkoutsLeft > 0 ?
                          "text-gold"
                        : "text-muted"
                      }`}
                    >
                      {hints.checkoutsLeft}
                    </span>
                  </div>
                </>
              )}
            </div>
          </section>

          {/* Question */}
          <section aria-label="Question">
            <div className="kicker mb-2">Question</div>
            <h1 className="font-display font-bold text-2xl md:text-[32px] leading-snug tracking-tight">
              {question || "Loading question…"}
            </h1>
          </section>

          {/* Answer input — anchored to bottom on mobile for thumb-zone reach */}
          <div className="max-lg:sticky max-lg:bottom-0 max-lg:bg-bg max-lg:border-t max-lg:border-line max-lg:pt-4 max-lg:pb-4 max-lg:-mx-4 max-lg:px-4 z-[20]">
          <section className="flex flex-col gap-3.5" aria-label="Answer">
            <div className="relative">
              <div className="flex items-center gap-3 bg-surface border border-line-strong rounded-md px-4 md:px-5 h-14 md:h-16 focus-within:border-accent transition-colors">
                <span
                  className="text-accent font-display font-bold text-xl select-none"
                  aria-hidden="true"
                >
                  ›
                </span>
                <EntitySearch
                  entityType={entityType}
                  onSelect={handleStage}
                  placeholder="Type a name…"
                  className="flex-1 bg-transparent border-0 outline-none text-ink text-lg md:text-xl font-sans font-medium placeholder:text-muted/60 p-0 min-w-0"
                  disabled={disabled}
                />
              </div>
            </div>

            {/* Staged answer */}
            {staged ?
              <div className="flex items-center justify-between gap-4 bg-ok-soft border border-ok/40 rounded-md px-4 md:px-5 py-3 animate-rise">
                <div className="flex items-center gap-3 min-w-0">
                  <span className="kicker text-ok">Lined up</span>
                  <span className="font-display font-bold text-lg truncate">
                    {staged.name}
                  </span>
                </div>
                <button
                  onClick={() => setStaged(null)}
                  className="text-muted hover:text-danger text-lg leading-none transition-colors p-2.5"
                  aria-label="Clear selection"
                >
                  ✕
                </button>
              </div>
            : <div className="hint border border-dashed border-line rounded-md px-4 md:px-5 py-3.5">
                Pick a name from the suggestions to line up your throw
              </div>
            }

            {/* Throw */}
            <button
              onClick={handleThrowDart}
              disabled={!staged || disabled}
              className="btn-primary h-14 text-lg tracking-wide w-full md:w-auto md:self-start md:px-12"
            >
              Throw dart
              <span aria-hidden="true">→</span>
            </button>
          </section>
          </div>
        </div>

        <MoveHistory moves={moves} turnCount={turnCount} />
      </main>

      {isGameOver && isWin && (
        <WinOverlay
          score={score}
          turnCount={turnCount}
          gameType={gameType}
          onShare={onShare}
          shareState={shareState}
          onPlayAgain={onPlayAgain}
          onExit={onExit}
        />
      )}

      {isGameOver && !isWin && (
        <LossOverlay
          score={score}
          turnCount={turnCount}
          gameType={gameType}
          onPlayAgain={onPlayAgain}
          onExit={onExit}
        />
      )}

      {/* ── Exit confirmation dialog ─────────────────────────────────────────── */}
      <ConfirmDialog
        open={showExitConfirm}
        title="Exit Game?"
        message="Are you sure you want to exit? Your progress in this game will be lost."
        confirmText="Exit"
        cancelText="Stay"
        type="danger"
        onConfirm={() => {
          setShowExitConfirm(false);
          onExit();
        }}
        onCancel={() => setShowExitConfirm(false)}
      />

      {/* ── Debug panel (admin only, Ctrl+Shift+D) ──────────────────────────────── */}
      {isAdmin && <DebugPanel gameId={gameId} gameType={gameType} />}
    </div>
  );
}
