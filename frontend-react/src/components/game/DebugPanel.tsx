"use client";

import { useState, useEffect } from "react";
import { gameApiClient } from "@/lib/api/GameApiClient";
import type { AnswerItem, GameType } from "@/lib/types/game";

interface DebugPanelProps {
  gameId: string | null;
  gameType: GameType;
}

export default function DebugPanel({ gameId, gameType }: DebugPanelProps) {
  const [open, setOpen] = useState(false);
  const [answers, setAnswers] = useState<AnswerItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open || !gameId) return;
    setLoading(true);
    setError(null);
    gameApiClient
      .getAllAnswers(gameId, gameType)
      .then((data) => setAnswers(data.sort((a, b) => b.score - a.score)))
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [open, gameId, gameType]);

  // Ctrl+Shift+D toggles the panel
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.ctrlKey && e.shiftKey && e.key === "D") {
        e.preventDefault();
        setOpen((v) => !v);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  if (!gameId) return null;

  return (
    <>
      {/* Toggle button */}
      <button
        onClick={() => setOpen((v) => !v)}
        className="fixed bottom-4 right-4 z-50 border border-line-strong bg-surface text-muted rounded-sm px-2 py-1 font-mono text-[11px] hover:text-ink transition-colors"
        title="Debug: Show all answers (Ctrl+Shift+D)"
      >
        {open ? "[x] DEBUG" : "[?] DEBUG"}
      </button>

      {/* Panel */}
      {open && (
        <div className="fixed bottom-14 right-4 z-50 w-96 max-h-[70vh] border border-line-strong bg-surface text-ink font-mono rounded-md shadow-[var(--shadow-pop)] flex flex-col overflow-hidden">
          <div className="bg-surface-2 px-3 py-1.5 text-[11px] tracking-wider flex justify-between items-center">
            <span>ALL ANSWERS ({answers.length})</span>
            <button
              onClick={() => setOpen(false)}
              className="hover:opacity-70 text-base leading-none"
            >
              ✕
            </button>
          </div>
          <div className="overflow-y-auto flex-1 p-2 text-[12px] scrollbar-thin">
            {loading && (
              <div className="text-muted py-4 text-center">Loading...</div>
            )}
            {error && (
              <div className="text-danger py-4 text-center">
                Error: {error}
              </div>
            )}
            {!loading &&
              !error &&
              answers.map((a) => (
                <div
                  key={a.id}
                  className={`flex justify-between items-baseline py-1 border-b border-line ${
                    !a.isValidDarts || a.isBust ? "opacity-50" : ""
                  }`}
                >
                  <span
                    className={`truncate flex-1 mr-2 ${
                      a.isBust
                        ? "text-danger line-through"
                        : !a.isValidDarts
                          ? "text-muted"
                          : "text-ok"
                    }`}
                  >
                    {a.displayText}
                  </span>
                  <span className="text-ink tabular-nums mr-2">
                    {a.score}
                  </span>
                  <span className="text-[11px] tracking-wider w-14 text-right">
                    {a.isBust ? (
                      <span className="text-danger">BUST</span>
                    ) : !a.isValidDarts ? (
                      <span className="text-muted">INV</span>
                    ) : (
                      <span className="text-ok">OK</span>
                    )}
                  </span>
                </div>
              ))}
            {!loading && !error && answers.length === 0 && (
              <div className="text-muted py-4 text-center">
                No answers found
              </div>
            )}
          </div>
        </div>
      )}
    </>
  );
}
