"use client";

import { useState, useCallback, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import dynamic from "next/dynamic";
import ThemeToggle from "@/components/ui/ThemeToggle";
import NavScreenRenderer from "./NavScreenRenderer";
import { gameApiClient } from "@/lib/api/GameApiClient";
import { saveGameState } from "@/hooks/useGamePersistence";
import { useToast } from "@/context/ToastContext";
import { useNavStack } from "@/hooks/useNavStack";
import type { FootballFilter } from "@/lib/api/footballApi";

const LoginButton = dynamic(() => import("@/components/auth/LoginButton"), { ssr: false });

// ─── Inner page (reads search params, must be inside Suspense) ─────────────

function FootballPageInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { addToast } = useToast();

  // Read target from URL, default to 501
  const targetParam = searchParams.get("target");
  const targetScore = targetParam === "301" ? 301 : targetParam === "101" ? 101 : 501;

  const [starting, setStarting] = useState<string | null>(null);
  const { stack, currentScreen, slideDir, animKey, breadcrumb, push, pop } = useNavStack();

  const startGame = useCallback(
    async (slug: string, label: string, filter?: FootballFilter) => {
      if (starting) return;
      setStarting(slug);
      try {
        const game = await gameApiClient.startFreePlay(slug, targetScore, filter);
        saveGameState(game.gameId, label, "freeplay");
        router.push("/");
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : "Failed to start game";
        addToast(message, "error");
      } finally {
        setStarting(null);
      }
    },
    [targetScore, router, addToast, starting],
  );

  return (
    <div className="relative min-h-screen bg-bg text-ink flex flex-col font-sans">
      {/* Header */}
      <header className="relative z-10 flex items-center justify-between px-5 md:px-10 py-4 border-b border-line">
        <div className="flex items-center gap-3">
          {stack.length > 1 ? (
            <button
              onClick={pop}
              className="font-mono text-lg text-muted hover:text-accent hover:-translate-x-0.5 transition-all"
              aria-label="Back"
            >
              ←
            </button>
          ) : (
            <Link href="/freeplay" className="font-mono text-lg text-muted hover:text-accent hover:-translate-x-0.5 transition-all" aria-label="Back to Free Play">
              ←
            </Link>
          )}
          <span className="bullseye" aria-hidden="true" />
          <Link href="/" className="font-display font-extrabold text-lg tracking-tight leading-none hover:opacity-80 transition-opacity no-underline text-ink">
            TRIVIA <span className="text-accent">501</span>
          </Link>
          <span className="kicker hidden sm:block ml-2">Football</span>
        </div>
        <div className="flex items-center gap-3">
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-accent text-bg font-display font-bold text-xs tracking-wide">
            🎯 {targetScore}
          </span>
          <ThemeToggle />
          <LoginButton />
        </div>
      </header>

      <main className="relative z-10 flex-1 flex flex-col px-5 md:px-10 py-6">
        {/* Breadcrumb back button when drilled down */}
        {stack.length > 1 && (
          <button
            onClick={pop}
            className="group flex items-center gap-4 py-3.5 px-2 -mx-2 rounded-sm border-b border-line hover:bg-surface transition-colors text-left w-full mb-4"
          >
            <span aria-hidden="true" className="font-mono text-lg text-muted group-hover:text-accent group-hover:-translate-x-0.5 transition-all">←</span>
            <span className="font-display font-bold text-xl text-ink">{breadcrumb || "Back"}</span>
          </button>
        )}

        {/* Animated content area */}
        <div key={animKey} className={slideDir === 1 ? "animate-nav-push" : "animate-nav-pop"}>
          <NavScreenRenderer
            screen={currentScreen!}
            onPush={push}
            onStartGame={startGame}
            starting={starting}
          />
        </div>
      </main>
    </div>
  );
}

// ─── Page (wraps inner in Suspense for useSearchParams) ─────────────────────

export default function FootballPage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen flex items-center justify-center bg-bg">
        <div className="animate-spin-slow rounded-full h-10 w-10 border-2 border-line border-t-accent" />
      </div>
    }>
      <FootballPageInner />
    </Suspense>
  );
}
