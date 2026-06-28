"use client";

interface LossOverlayProps {
  score: number;
  turnCount: number;
  gameType: "freeplay" | "daily-challenge";
  onPlayAgain: () => void;
  onExit: () => void;
}

export default function LossOverlay({
  score,
  turnCount,
  gameType,
  onPlayAgain,
  onExit,
}: LossOverlayProps) {
  return (
    <div className="fixed inset-0 bg-bg/95 backdrop-blur-sm flex flex-col items-center justify-center z-50 gap-6 p-6 animate-fade-in">
      <div className="text-center animate-rise">
        <div
          className="display-num text-danger"
          style={{ fontSize: "clamp(80px, 16vw, 130px)" }}
        >
          {score}
        </div>
        <div className="kicker text-danger mt-2">Game over</div>
      </div>

      <div
        className="text-center animate-rise"
        style={{ animationDelay: "0.1s" }}
      >
        <div className="font-display font-extrabold text-2xl md:text-3xl tracking-tight">
          Better luck next time
        </div>
        <div className="kicker mt-2">
          {turnCount} {turnCount === 1 ? "dart" : "darts"} thrown · finished on {score}
        </div>
      </div>

      <div
        className="flex flex-col gap-3 items-center mt-2 w-full max-w-xs animate-rise"
        style={{ animationDelay: "0.2s" }}
      >
        {gameType !== "daily-challenge" && (
          <button
            onClick={onPlayAgain}
            className="btn-primary w-full h-12 text-base"
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
