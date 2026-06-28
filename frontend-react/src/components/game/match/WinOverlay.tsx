"use client";

interface WinOverlayProps {
  score: number;
  turnCount: number;
  gameType: "freeplay" | "daily-challenge";
  onShare?: () => void;
  shareState?: "idle" | "sharing" | "copied";
  onPlayAgain: () => void;
  onExit: () => void;
}

export default function WinOverlay({
  score,
  turnCount,
  gameType,
  onShare,
  shareState = "idle",
  onPlayAgain,
  onExit,
}: WinOverlayProps) {
  return (
    <div className="fixed inset-0 bg-bg/95 backdrop-blur-sm flex flex-col items-center justify-center z-50 gap-7 p-6 animate-fade-in">
      <div className="relative flex items-center justify-center w-56 h-56">
        <span className="ring-burst" aria-hidden="true" />
        <span className="ring-burst ring-burst-2" aria-hidden="true" />
        <div className="text-center animate-rise">
          <div
            className="display-num text-gold"
            style={{ fontSize: "96px" }}
          >
            {score <= 0 ? 0 : score}
          </div>
          <div className="kicker text-gold mt-1">Checkout</div>
        </div>
      </div>

      <div
        className="text-center animate-rise"
        style={{ animationDelay: "0.1s" }}
      >
        <div className="font-display font-extrabold text-3xl md:text-4xl tracking-tight">
          Game shot!
        </div>
        <div className="kicker mt-2">
          {turnCount} {turnCount === 1 ? "dart" : "darts"} thrown
        </div>
      </div>

      <div
        className="flex flex-col gap-3 items-center mt-2 w-full max-w-xs animate-rise"
        style={{ animationDelay: "0.2s" }}
      >
        {onShare && (
          <button
            onClick={onShare}
            disabled={shareState !== "idle"}
            className="btn-primary w-full h-12 text-base"
          >
            {shareState === "copied" ? "Copied ✓"
            : shareState === "sharing" ? "Sharing…"
            : "Share result"}
          </button>
        )}
        {gameType !== "daily-challenge" && (
          <button
            onClick={onPlayAgain}
            className={`${onShare ? "btn-ghost" : "btn-primary"} w-full h-12 text-base`}
          >
            Play again
          </button>
        )}
        <button
          onClick={() => onExit()}
          className="kicker hover:text-ink transition-colors py-2"
        >
          Exit to lobby
        </button>
      </div>
    </div>
  );
}
