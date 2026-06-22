"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  useDailyChallenge,
  type CategoryChallenge,
} from "@/hooks/useDailyChallenge";
import { apiFetch } from "@/lib/api/client";
import { useToast } from "@/context/ToastContext";
import ThemeToggle from "@/components/ui/ThemeToggle";
import ConfirmDialog from "@/components/ui/ConfirmDialog";

export default function DailyPage() {
  const router = useRouter();
  const { challenges, loading, error, date, refresh } = useDailyChallenge();
  const { addToast } = useToast();
  const [starting, setStarting] = useState<string | null>(null);
  const [pendingDaily, setPendingDaily] = useState<{ slug: string; label: string } | null>(null);

  const handlePlay = async (slug: string, label: string) => {
    setStarting(slug);
    try {
      const res = await apiFetch(
        `/api/daily-challenge/${encodeURIComponent(slug)}/start`,
        {
          method: "POST",
        },
      );
      if (!res.ok) {
        const text = await res.text().catch(() => "");
        let msg = "Failed to start challenge";
        try {
          const p = JSON.parse(text);
          msg = p.error || p.message || text;
        } catch {
          msg = text || msg;
        }
        throw new Error(msg);
      }
      const game = await res.json();
      // Store game state and redirect to main page (will restore from sessionStorage)
      sessionStorage.setItem(
        "activeGameState",
        JSON.stringify({
          gameId: game.gameId,
          label: label,
          gameType: "daily-challenge",
        }),
      );
      router.push("/");
    } catch (err) {
      addToast(
        (err as Error).message || "Error starting daily challenge",
        "error",
      );
    } finally {
      setStarting(null);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bg">
        <div className="text-center">
          <div className="animate-spin-slow rounded-full h-10 w-10 border-2 border-line border-t-accent mx-auto mb-4" />
          <p className="kicker">Loading challenges…</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg text-ink font-sans">
      <div className="max-w-4xl mx-auto px-5 md:px-6 py-8 md:py-10">
        <header className="border-b border-line pb-6 mb-8">
          <div className="flex items-start justify-between gap-4">
            <div>
              <div className="flex items-center gap-2.5 mb-2">
                <span
                  className="w-2 h-2 rounded-full bg-gold"
                  aria-hidden="true"
                />
                <span className="kicker">
                  {date ?
                    new Date(date + "T12:00:00").toLocaleDateString("en-GB", {
                      weekday: "short",
                      day: "numeric",
                      month: "short",
                      year: "numeric",
                    })
                  : "Today"}
                </span>
              </div>
              <h1 className="font-display font-extrabold text-3xl md:text-4xl tracking-tight">
                Daily Challenges
              </h1>
            </div>
            <ThemeToggle />
          </div>
          <p className="text-muted text-sm mt-3 max-w-md">
            One question per category. Everyone gets the same target. Share your
            result.
          </p>
        </header>

        {error && (
          <>
            <div className="text-danger text-center py-6 text-sm">{error}</div>
            <button
              onClick={() => refresh()}
              disabled={loading}
              className="btn-primary mt-auto h-12 text-base w-full"
            >
              {loading ? "Refreshing…" : "Refresh"}
            </button>
          </>
        )}

        {!error && challenges.length === 0 && (
          <div className="text-center py-16">
            <div className="font-display font-bold text-xl mb-2">
              No challenges today
            </div>
            <p className="text-muted text-sm">
              Check back at midnight UTC for new challenges.
            </p>
          </div>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 md:gap-6">
          {challenges.map((dc: CategoryChallenge) => {
            const lock = dc.lockState;
            const isCompleted = lock?.state === "completed";
            const isInProgress = lock?.state === "in_progress";
            return (
              <div
                key={dc.categorySlug}
                className={[
                  "bg-surface border rounded-md p-6 flex flex-col transition-all duration-200",
                  isCompleted ?
                    "border-line opacity-70"
                  : "border-line hover:border-line-strong hover:shadow-[var(--shadow-card)]",
                ].join(" ")}
              >
                <div className="flex items-baseline justify-between mb-3">
                  <span className="font-display font-bold text-xl tracking-tight">
                    {dc.categoryName}
                  </span>
                  <span className="font-mono text-[9px] tracking-[0.2em] text-gold uppercase">
                    {isCompleted ? "Played" : "Daily"}
                  </span>
                </div>
                <div className="display-num text-[56px] mb-2">
                  {dc.startingScore}
                </div>
                <div className="text-muted text-sm leading-snug mb-5 line-clamp-2">
                  {dc.questionText || "Loading…"}
                </div>
                {isCompleted ?
                  <a
                    href={`/daily/${dc.categorySlug}`}
                    className="btn-secondary mt-auto h-12 text-base w-full flex items-center justify-center"
                  >
                    View result →
                  </a>
                : <button
                    onClick={() => {
                      if (isInProgress) {
                        handlePlay(dc.categorySlug, dc.categoryName);
                      } else {
                        setPendingDaily({ slug: dc.categorySlug, label: dc.categoryName });
                      }
                    }}
                    disabled={starting === dc.categorySlug}
                    className="btn-primary mt-auto h-12 text-base w-full"
                  >
                    {starting === dc.categorySlug ? "Starting…"
                    : isInProgress ? "Resume →"
                    : "Play now"}
                  </button>
                }
              </div>
            );
          })}
        </div>

        <footer className="mt-12 pt-6 border-t border-line text-center">
          <a href="/" className="kicker hover:text-ink transition-colors">
            ← Back to lobby
          </a>
        </footer>
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
            handlePlay(pendingDaily.slug, pendingDaily.label);
          }
          setPendingDaily(null);
        }}
        onCancel={() => setPendingDaily(null)}
      />
    </div>
  );
}
