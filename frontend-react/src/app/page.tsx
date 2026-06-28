"use client";

import { useState, useEffect, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import dynamic from "next/dynamic";
import DailyHeroSection from "@/components/game/lobby/DailyHeroSection";
import MatchView from "@/components/game/match/MatchView";
import AnimatedScorePopup from "@/components/game/AnimatedScorePopup";
import ErrorBoundary from "@/components/ErrorBoundary";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import ThemeToggle from "@/components/ui/ThemeToggle";
import { useGameLoop, getSavedLabel } from "@/hooks/useGameLoop";
import { useDailyChallenge } from "@/hooks/useDailyChallenge";
import { useCountdown } from "@/hooks/useCountdown";
import { useToast } from "@/context/ToastContext";
import { apiFetch } from "@/lib/api/client";
import { buildShareText } from "@/utils/share";

const LoginButton = dynamic(() => import("@/components/auth/LoginButton"), { ssr: false });

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Derive a display category name from the selection label (e.g. "Football > Premier League > Goals > Random") */
function categoryLabel(label: string): { name: string; sub: string } {
  const parts = label.split(" > ");
  const name = parts[0] ?? "Trivia";
  const sub = parts.slice(1).join(" > ") || "Darts Edition";
  return { name, sub };
}

// ─── Auth redirect handler (must be inside Suspense — uses useSearchParams) ───

function AuthRequiredRedirect() {
  const searchParams = useSearchParams();
  const { addToast } = useToast();
  useEffect(() => {
    if (searchParams.get("auth_required")) {
      addToast("Please sign in to access admin pages", "info");
      const url = new URL(window.location.href);
      url.searchParams.delete("auth_required");
      window.history.replaceState({}, "", url.toString());
    }
  }, [searchParams, addToast]);
  return null;
}

// ─── Component ────────────────────────────────────────────────────────────────

export default function GamePage() {
  // Game loop — owns all game-session state and API calls
  const {
    score,
    question,
    turnCount,
    gameStatus,
    isWin,
    moves,
    entityType,
    hints,
    isAnimating,
    flashVersion,
    popup,
    gameType,
    gameId,
    onPopupComplete,
    startNewGame,
    startDailyChallenge,
    submitAnswer,
    exitGame,
  } = useGameLoop();

  // Daily challenge status
  const {
    challenges: dailyChallenges,
    loading: dailyLoading,
    error: dailyError,
    refresh: retryDailies,
  } = useDailyChallenge();

  const { addToast } = useToast();
  const timeUntilReset = useCountdown();

  // Share state: idle → sharing → copied
  const [shareState, setShareState] = useState<"idle" | "sharing" | "copied">("idle");

  // Track the last selection so we can replay and display in MatchView.
  const savedLabel = getSavedLabel();
  const [lastSlug, setLastSlug] = useState(() => savedLabel ?? "football");
  const [lastLabel, setLastLabel] = useState(() => savedLabel ?? "Football");

  // Pending daily challenge confirmation
  const [pendingDaily, setPendingDaily] = useState<{ slug: string; label: string } | null>(null);
  // Slug currently starting a game (prevents double-clicks)
  const [starting, setStarting] = useState<string | null>(null);

  // ── Handlers ─────────────────────────────────────────────────────────────────

  const handleStartDailyChallenge = async (categorySlug: string, label: string) => {
    if (starting) return;
    setStarting(categorySlug);
    setLastSlug(categorySlug);
    setLastLabel(label);
    try {
      await startDailyChallenge(categorySlug, label);
    } finally {
      setStarting(null);
    }
  };

  const handlePlayAgain = async () => {
    await startNewGame(lastSlug, lastLabel);
  };

  const handleShare = async () => {
    if (!gameId || shareState !== "idle") return;
    setShareState("sharing");
    try {
      const res = await apiFetch(`/api/daily-challenge/share/${gameId}`);
      if (!res.ok) throw new Error("Failed to get share data");
      const data = await res.json();

      const shareText = buildShareText(data, window.location.origin);

      if (navigator.share) {
        await navigator.share({ text: shareText });
      } else {
        await navigator.clipboard.writeText(shareText);
      }
      setShareState("copied");
      setTimeout(() => setShareState("idle"), 2000);
      addToast("Result copied to clipboard!", "success");
    } catch (err) {
      if ((err as Error).name !== "AbortError") {
        addToast("Failed to share result", "error");
      }
      setShareState("idle");
    }
  };

  // ── Restoring state ─────────────────────────────────────────────────────────

  const authRedirect = (
    <Suspense fallback={null}>
      <AuthRequiredRedirect />
    </Suspense>
  );

  if (gameStatus === "RESTORING") {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bg">
        {authRedirect}
        <div className="text-center">
          <div className="animate-spin-slow rounded-full h-10 w-10 border-2 border-line border-t-accent mx-auto mb-4" />
          <p className="kicker">Restoring game…</p>
        </div>
      </div>
    );
  }

  // ── Lobby ───────────────────────────────────────────────────────────────────

  if (gameStatus === "NOT_STARTED") {
    return (
      <ErrorBoundary section="lobby">
        {authRedirect}
        <div className="animate-fade-in relative min-h-screen bg-bg text-ink flex flex-col font-sans">
          {/* Header */}
          <header className="relative z-10 flex items-center justify-between px-5 md:px-10 py-4 border-b border-line">
            <div className="flex items-center gap-3">
              <span className="bullseye" aria-hidden="true" />
              <Link href="/" className="font-display font-extrabold text-lg tracking-tight leading-none hover:opacity-80 transition-opacity no-underline text-ink">
                TRIVIA <span className="text-accent">501</span>
              </Link>
              <span className="kicker hidden sm:block ml-2">The trivia darts championship</span>
            </div>
            <div className="flex items-center gap-3">
              <ThemeToggle />
              <LoginButton />
            </div>
          </header>

          <main className="relative z-10 flex-1 flex flex-col px-5 md:px-10 py-8">
            {/* Hero Daily Challenges */}
            <DailyHeroSection
              challenges={dailyChallenges}
              loading={dailyLoading}
              error={dailyError}
              onRetry={retryDailies}
              timeUntilReset={timeUntilReset}
              starting={starting}
              onPlay={handleStartDailyChallenge}
              onRequestConfirm={(slug, label) => setPendingDaily({ slug, label })}
            />

            {/* Build Your Own Game entry */}
            <div className="border-t border-line pt-8 mt-2">
              <Link
                href="/freeplay"
                className="group flex flex-col bg-surface border border-line rounded-lg p-6 md:p-8 text-left transition-all duration-200 hover:-translate-y-0.5 hover:border-line-strong hover:shadow-[var(--shadow-card)]"
              >
                <span className="font-display font-bold text-xl md:text-2xl mb-2">
                  Build Your Own Game
                </span>
                <span className="text-muted text-sm leading-snug mb-5">
                  Pick your category, choose a starting score, and play on your own terms. No daily lock — replay as many times as you want.
                </span>
                <span className="inline-flex items-center gap-1.5 px-5 py-2.5 rounded-full bg-accent text-bg font-display font-bold text-sm tracking-wide group-hover:shadow-lg group-hover:scale-105 transition-all self-end">
                  BUILD YOUR GAME <span aria-hidden="true">→</span>
                </span>
              </Link>
            </div>
          </main>
        </div>

        <ConfirmDialog
          open={pendingDaily !== null}
          title={`Play today's ${pendingDaily?.label} challenge?`}
          message="You only get one attempt per day. Once you start, this is your shot."
          confirmText="Let's go"
          cancelText="Not yet"
          type="info"
          onConfirm={() => {
            if (pendingDaily) {
              handleStartDailyChallenge(pendingDaily.slug, pendingDaily.label);
            }
            setPendingDaily(null);
          }}
          onCancel={() => setPendingDaily(null)}
        />
      </ErrorBoundary>
    );
  }

  // ── Active game ──────────────────────────────────────────────────────────────

  const { name: catName, sub: catSub } = categoryLabel(lastLabel);

  return (
    <ErrorBoundary section="game">
      {authRedirect}
      <div className="animate-fade-in">
        <MatchView
          score={score}
          question={question}
          turnCount={turnCount}
          moves={moves}
          onExit={exitGame}
          onSubmitAnswer={submitAnswer}
          onPlayAgain={handlePlayAgain}
          categoryName={catName}
          categorySub={catSub}
          entityType={entityType}
          isWin={isWin}
          isGameOver={gameStatus === "COMPLETED"}
          hints={hints}
          disabled={isAnimating}
          flashVersion={flashVersion}
          onShare={gameType === "daily-challenge" ? handleShare : undefined}
          shareState={shareState}
          gameId={gameId}
          gameType={gameType}
        />
      </div>
      {popup && (
        <AnimatedScorePopup
          scoreValue={popup.scoreValue}
          result={popup.result}
          reason={popup.reason}
          onComplete={onPopupComplete}
        />
      )}
    </ErrorBoundary>
  );
}

