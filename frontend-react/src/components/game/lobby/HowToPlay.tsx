"use client";

import { useState, useEffect } from "react";

const STORAGE_KEY = "t501-onboarding-seen";

/**
 * One-time welcome card shown to first-time visitors on the lobby.
 * Dismissed with a localStorage flag — never shown again.
 *
 * Provides a 15-second primer on darts 501 scoring so the player
 * understands the core loop before committing to a daily challenge.
 */
export default function HowToPlay() {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    try {
      if (localStorage.getItem(STORAGE_KEY) !== "1") {
        setVisible(true);
      }
    } catch {
      // private mode — localStorage unavailable, show it anyway
      setVisible(true);
    }
  }, []);

  const dismiss = () => {
    setVisible(false);
    try {
      localStorage.setItem(STORAGE_KEY, "1");
    } catch {
      // private mode
    }
  };

  if (!visible) return null;

  return (
    <div className="animate-rise bg-surface border border-line rounded-lg p-5 md:p-6 mb-6">
      <div className="flex items-start justify-between gap-4 mb-4">
        <div className="flex items-center gap-2.5">
          <span
            className="w-2.5 h-2.5 rounded-full bg-accent"
            aria-hidden="true"
          />
          <span className="font-display font-bold text-lg tracking-tight">
            How to Play
          </span>
        </div>
        <button
          onClick={dismiss}
          className="btn-ghost px-3 py-1.5 text-[10px]"
          aria-label="Dismiss how to play"
        >
          Got it
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Step
          num={1}
          title="Read the question"
          body="Each challenge gives you a target score and a question — like
          &ldquo;Appearances for Arsenal in the Premier League.&rdquo;"
        />
        <Step
          num={2}
          title="Name players, score down"
          body="Type a player&rsquo;s name. Their stat (e.g. 258 appearances) is
          deducted from your score — just like darts 501."
        />
        <Step
          num={3}
          title="Hit zero to win"
          body="Land in the checkout zone (&minus;10 to 0) to finish. Watch out —
          some scores are bust and score nothing, like 163, 166, or 169."
        />
      </div>
    </div>
  );
}

function Step({
  num,
  title,
  body,
}: {
  num: number;
  title: string;
  body: string;
}) {
  return (
    <div className="flex gap-3">
      <span
        className="flex-shrink-0 w-6 h-6 rounded-full bg-surface-2 flex items-center justify-center font-mono text-[11px] font-medium text-muted"
        aria-hidden="true"
      >
        {num}
      </span>
      <div>
        <p className="font-display font-bold text-sm mb-1">{title}</p>
        <p className="text-muted text-xs leading-relaxed">{body}</p>
      </div>
    </div>
  );
}
