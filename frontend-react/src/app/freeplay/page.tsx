"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import dynamic from "next/dynamic";
import CategoryCard from "@/components/game/lobby/CategoryCard";
import ThemeToggle from "@/components/ui/ThemeToggle";
import HowToPlayPanel from "@/components/game/HowToPlayPanel";
import { gameApiClient } from "@/lib/api/GameApiClient";
import { saveGameState } from "@/hooks/useGamePersistence";
import { useToast } from "@/context/ToastContext";
import { OTHER_CATEGORIES, resolveTarget, type TargetScore, TARGET_OPTIONS } from "@/components/game/lobby/types";

const LoginButton = dynamic(() => import("@/components/auth/LoginButton"), { ssr: false });

export default function FreePlayHubPage() {
  const router = useRouter();
  const { addToast } = useToast();
  const [target, setTarget] = useState<TargetScore>(501);
  const [starting, setStarting] = useState<string | null>(null);

  const startGame = async (slug: string, label: string) => {
    if (starting) return;
    setStarting(slug);
    try {
      const game = await gameApiClient.startFreePlay(slug, resolveTarget(target));
      saveGameState(game.gameId, label, "freeplay");
      router.push("/");
    } catch (err: any) {
      addToast(err?.message ?? "Failed to start game", "error");
    } finally {
      setStarting(null);
    }
  };

  return (
    <div className="animate-fade-in relative min-h-screen bg-bg text-ink flex flex-col font-sans">
      {/* Header */}
      <header className="relative z-10 flex items-center justify-between px-5 md:px-10 py-4 border-b border-line">
        <div className="flex items-center gap-3">
          <Link href="/" className="font-mono text-lg text-muted hover:text-accent hover:-translate-x-0.5 transition-all" aria-label="Back to home">
            ←
          </Link>
          <span className="bullseye" aria-hidden="true" />
          <span className="font-display font-extrabold text-lg tracking-tight leading-none">
            TRIVIA <span className="text-accent">501</span>
          </span>
          <span className="kicker hidden sm:block ml-2">Build Your Own Game</span>
        </div>
        <div className="flex items-center gap-3">
          <ThemeToggle />
          <LoginButton />
        </div>
      </header>

      <main className="relative z-10 flex-1 flex flex-col px-5 md:px-10 py-8 max-w-2xl mx-auto w-full">
        {/* Target score selector */}
        <div className="mb-8 p-5 rounded-lg bg-ink/[0.04]">
          <div className="flex items-baseline gap-2 mb-3">
            <span className="font-display font-bold text-lg text-ink">Starting Score</span>
            <span className="font-mono text-[10px] tracking-[0.12em] text-muted">— pick your target —</span>
          </div>
          <div className="flex items-center gap-2.5 flex-wrap">
            <span className="font-mono text-[11px] text-muted">Score:</span>
            {TARGET_OPTIONS.map((opt) => (
              <button
                key={opt}
                onClick={() => setTarget(opt)}
                aria-pressed={target === opt}
                className={`font-mono text-sm font-bold tracking-[0.06em] px-4 py-2 rounded-full border-2 transition-all duration-200 ${
                  target === opt
                    ? "bg-ink text-bg border-ink"
                    : "bg-transparent text-muted border-line hover:border-line-strong hover:text-ink"
                }`}
              >
                {opt === "random" ? "RND" : opt}
              </button>
            ))}
          </div>
        </div>

        {/* Category cards */}
        <div className="flex flex-col gap-4">
          <CategoryCard
            name="Football"
            description="Goals, assists, appearances across 5 leagues"
            onClick={() => router.push(`/freeplay/football?target=${target}`)}
            hasChildren
            disabled={starting !== null}
          />

          <span className="kicker text-[10px] tracking-[0.12em] text-muted mt-2">More Categories</span>

          {OTHER_CATEGORIES.map((cat) => (
            <CategoryCard
              key={cat.id}
              name={cat.name}
              description={cat.description}
              onClick={() => startGame(cat.id, cat.name)}
              disabled={starting !== null}
              loading={starting === cat.id}
            />
          ))}
        </div>

        <div className="mt-8">
          <HowToPlayPanel />
        </div>
      </main>
    </div>
  );
}
