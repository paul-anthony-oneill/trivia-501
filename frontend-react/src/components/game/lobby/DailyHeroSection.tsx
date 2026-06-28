"use client";

import Link from "next/link";
import type { CategoryChallenge } from "@/hooks/useDailyChallenge";

// ─── Props ──────────────────────────────────────────────────────────────────

interface DailyHeroSectionProps {
  challenges: CategoryChallenge[];
  loading: boolean;
  error: string | null;
  onRetry: () => void;
  timeUntilReset: string;
  starting: string | null;
  onPlay: (slug: string, label: string) => void;
  onRequestConfirm: (slug: string, label: string) => void;
}

// ─── Shared card content (label + name + score + question) ──────────────────

function HeroCardContent({
  label,
  name,
  score,
  questionText,
}: {
  label: string;
  name: string;
  score: number;
  questionText: string;
}) {
  return (
    <>
      <span className="font-mono text-[10px] tracking-[0.2em] text-gold mb-3">{label}</span>
      <span className="font-display font-bold text-xl md:text-2xl mb-3">{name}</span>
      <div className="display-num text-[72px] md:text-[88px] mb-3 leading-none">{score}</div>
      <p className="text-muted text-sm leading-snug line-clamp-2 mb-5">{questionText || "Loading…"}</p>
    </>
  );
}

// ─── Component ──────────────────────────────────────────────────────────────

export default function DailyHeroSection({
  challenges,
  loading,
  error,
  onRetry,
  timeUntilReset,
  starting,
  onPlay,
  onRequestConfirm,
}: DailyHeroSectionProps) {
  return (
    <section className="mb-10" suppressHydrationWarning>
      {/* Section header */}
      <div className="flex items-center gap-2.5 mb-6">
        <span className="w-2.5 h-2.5 rounded-full bg-gold" aria-hidden="true" />
        <span className="kicker text-[11px] tracking-[0.15em]">Today&apos;s Challenges</span>
        {timeUntilReset && (
          <span className="font-mono text-[11px] text-muted tabular-nums">
            Resets in {timeUntilReset}
          </span>
        )}
      </div>

      {/* Loading — skeleton hero cards */}
      {loading && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {[1, 2].map((i) => (
            <div
              key={i}
              className="bg-surface border border-line rounded-lg p-6 md:p-8 animate-pulse"
            >
              <div className="h-4 bg-line rounded w-20 mb-4" />
              <div className="h-7 bg-line rounded w-40 mb-4" />
              <div className="h-[88px] bg-line rounded w-32 mb-4" />
              <div className="h-4 bg-line rounded w-full mb-2" />
              <div className="h-4 bg-line rounded w-3/4" />
            </div>
          ))}
        </div>
      )}

      {/* Error */}
      {error && !loading && (
        <div className="flex items-center gap-3 bg-surface border border-line rounded-md px-4 py-3 text-sm text-muted">
          <span className="flex-1">Couldn&apos;t load today&apos;s challenges.</span>
          <button
            onClick={onRetry}
            className="kicker text-accent hover:text-ink transition-colors shrink-0"
          >
            Retry
          </button>
        </div>
      )}

      {/* Cards */}
      {!loading && !error && challenges.length > 0 && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {challenges.map((dc) => {
            const isThisStarting = starting === dc.categorySlug;
            const lock = dc.lockState;
            const isCompleted = lock?.state === "completed";
            const isInProgress = lock?.state === "in_progress";

            // Completed card — dimmed, links to /daily/[slug]
            if (isCompleted) {
              return (
                <Link
                  key={dc.categorySlug}
                  href={`/daily/${dc.categorySlug}`}
                  className="group flex flex-col bg-surface border border-line rounded-lg p-6 md:p-8 text-left transition-all duration-200 opacity-60 hover:opacity-80"
                >
                  <HeroCardContent
                    label="PLAYED"
                    name={dc.categoryName}
                    score={dc.startingScore}
                    questionText={dc.questionText || "Loading…"}
                  />
                  <div className="mt-auto">
                    <span className="inline-flex items-center gap-1.5 px-4 py-2 rounded-full border border-line text-muted font-display font-bold text-xs tracking-wide group-hover:border-muted group-hover:text-ink transition-colors">
                      VIEW RESULT <span aria-hidden="true">→</span>
                    </span>
                  </div>
                </Link>
              );
            }

            // Fresh or in-progress card
            return (
              <button
                key={dc.categorySlug}
                onClick={() => {
                  if (isInProgress) {
                    onPlay(dc.categorySlug, dc.categoryName);
                  } else {
                    onRequestConfirm(dc.categorySlug, dc.categoryName);
                  }
                }}
                disabled={starting !== null}
                className="group flex flex-col bg-surface border border-line rounded-lg p-6 md:p-8 text-left transition-all duration-200 hover:-translate-y-0.5 hover:border-line-strong hover:shadow-[var(--shadow-card)] disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:translate-y-0"
              >
                <HeroCardContent
                  label={isInProgress ? "IN PROGRESS" : "DAILY"}
                  name={dc.categoryName}
                  score={dc.startingScore}
                  questionText={dc.questionText || "Loading…"}
                />
                <div className="mt-auto">
                  <span className="inline-flex items-center gap-1.5 px-5 py-2.5 rounded-full bg-accent text-bg font-display font-bold text-sm tracking-wide transition-all group-hover:shadow-lg group-hover:scale-105">
                    {isThisStarting ? "STARTING…"
                     : isInProgress ? "RESUME →"
                     : "PLAY NOW →"}
                  </span>
                </div>
              </button>
            );
          })}
        </div>
      )}

      {/* Empty state — no challenges today (edge case) */}
      {!loading && !error && challenges.length === 0 && (
        <p className="text-muted text-sm py-6">
          No challenges available today — check back soon.
        </p>
      )}
    </section>
  );
}
