"use client";

import HowToPlayPanel from "../HowToPlayPanel";
import type { Move } from "@/hooks/useGameLoop";

interface MoveHistoryProps {
  moves: Move[];
  turnCount: number;
}

export default function MoveHistory({ moves, turnCount }: MoveHistoryProps) {
  return (
    <aside className="flex flex-col bg-surface border border-line rounded-md p-5 min-h-0 lg:max-h-[calc(100vh-120px)] lg:sticky lg:top-8">
      <div className="flex items-baseline justify-between mb-3">
        <span className="kicker">Match history</span>
        <span className="font-mono text-[10px] text-muted tabular-nums">
          TURN {turnCount.toString().padStart(2, "0")}
        </span>
      </div>

      <div className="flex-1 overflow-y-auto scrollbar-thin -mr-2 pr-2">
        {moves.length === 0 ?
          <div className="hint text-center py-8">No darts thrown yet</div>
        : moves.map((move, i) => (
            <div
              key={i}
              className="py-2.5 border-b border-line last:border-b-0"
            >
              <div className="grid grid-cols-[24px_1fr_auto_52px] gap-2.5 items-baseline">
                <span className="font-mono text-[10px] text-muted tabular-nums">
                  {(moves.length - i).toString().padStart(2, "0")}
                </span>
                <span
                  className={`font-sans font-medium text-sm truncate ${
                    move.result === "BUST" ? "text-muted line-through"
                    : move.result === "INVALID" ? "text-muted"
                    : "text-ink"
                  }`}
                >
                  {move.matchedAnswer || move.answer}
                </span>
                <span
                  className={`font-mono text-[11px] font-medium tabular-nums px-1.5 py-0.5 rounded-xs ${
                    move.result === "VALID" ? "text-ok bg-ok-soft"
                    : move.result === "BUST" ? "text-danger bg-danger-soft"
                    : "text-muted bg-surface-2"
                  }`}
                >
                  {move.result === "INVALID" ?
                    "✗"
                  : move.result === "BUST" ?
                    "BUST"
                  : `−${move.scoreValue}`}
                </span>
                <span className="font-display font-bold text-sm text-right tabular-nums">
                  {move.scoreAfter}
                </span>
              </div>
              {move.reason &&
                (move.result === "BUST" || move.result === "INVALID") && (
                  <div className="mt-1 ml-[34px] text-[11px] text-muted leading-snug">
                    {move.reason}
                  </div>
                )}
            </div>
          ))
        }
      </div>

      <div className="mt-4 pt-4 border-t border-line">
        <HowToPlayPanel />
      </div>
    </aside>
  );
}
