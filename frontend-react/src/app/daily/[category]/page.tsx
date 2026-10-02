"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { startGame } from "@/lib/gameStart";
import { useToast } from "@/context/ToastContext";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import {
  useDailyStatus,
  useDailyStart,
  dailyResultText,
} from "@/hooks/useDailyChallenge";

export default function DailyCategoryPage() {
  const params = useParams();
  const router = useRouter();
  const categorySlug = params.category as string;
  const { addToast } = useToast();

  const { status, lock, loading, error } = useDailyStatus(categorySlug);
  const { starting, play, confirmProps } = useDailyStart(async (spec) => {
    await startGame(spec);
    router.push("/");
  });
  const [resultText, setResultText] = useState<string | null>(null);

  const handleViewResult = async () => {
    if (!lock?.gameId) return;
    try {
      setResultText(await dailyResultText(lock.gameId));
    } catch {
      addToast("Couldn't load your result", "error");
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bg">
        <div className="text-center">
          <div className="animate-spin-slow rounded-full h-10 w-10 border-2 border-line border-t-accent mx-auto mb-4" />
          <p className="kicker">Loading…</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center bg-bg text-ink font-sans gap-5 p-6">
        <div className="font-display font-bold text-xl text-center">{error}</div>
        <a href="/daily" className="kicker text-accent hover:underline">
          ← View all challenges
        </a>
      </div>
    );
  }

  if (!status) return null;

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-bg text-ink font-sans p-5">
      <div className="max-w-md w-full bg-surface border border-line rounded-md p-7 md:p-8 shadow-[var(--shadow-card)]">
        <div className="flex items-baseline justify-between mb-5">
          <h1 className="font-display font-extrabold text-2xl tracking-tight">
            {status.categoryName} Daily
          </h1>
          <span className="font-mono text-[9px] tracking-[0.2em] text-gold uppercase">Today</span>
        </div>

        <div className="display-num text-[84px] text-center mb-4">
          {status.startingScore}
        </div>
        <div className="kicker text-center mb-6">Target score</div>

        <p className="text-muted text-sm leading-relaxed mb-8 text-center">
          {status.questionText}
        </p>

        {lock?.state === "completed" ?
          resultText ?
            <pre className="font-mono text-sm whitespace-pre-wrap select-all text-center">
              {resultText}
            </pre>
          : <button
              onClick={handleViewResult}
              className="btn-secondary w-full h-13 text-lg py-3.5"
            >
              View result →
            </button>
        : <button
            onClick={() => play(categorySlug, status.categoryName)}
            disabled={starting !== null}
            className="btn-primary w-full h-13 text-lg py-3.5"
          >
            {starting ? "Starting…"
            : lock?.state === "in_progress" ? "Resume →"
            : "Play now"}
          </button>
        }

        <div className="mt-6 text-center">
          <a href="/daily" className="kicker hover:text-ink transition-colors">
            ← All challenges
          </a>
        </div>
      </div>

      <ConfirmDialog {...confirmProps} />

      <footer className="mt-8 kicker">
        Shared via Trivia 501 ·{" "}
        <a href="/daily" className="text-accent hover:underline">
          /daily
        </a>
      </footer>
    </div>
  );
}
