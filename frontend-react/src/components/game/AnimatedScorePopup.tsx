"use client";

import { useState, useEffect, useRef } from "react";

interface AnimatedScorePopupProps {
  scoreValue: number;
  result: "VALID" | "BUST" | "INVALID";
  reason?: string;
  onComplete: () => void;
}

type Phase = "counting" | "flashing" | "showing" | "invalid";

export default function AnimatedScorePopup({
  scoreValue,
  result,
  reason,
  onComplete,
}: AnimatedScorePopupProps) {
  const prefersReducedMotion =
    typeof window !== "undefined" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  const [display, setDisplay] = useState(0);
  const [phase, setPhase] = useState<Phase>(() => {
    if (result === "INVALID") return "invalid";
    if (prefersReducedMotion) return "showing";
    return "counting";
  });
  const frameRef = useRef<number | null>(null);

  const target = scoreValue;

  // ── Skip: jump to the result immediately ─────────────────────────────────
  // Uses a ref so the keydown listener (registered once) always has fresh phase.
  const skipRef = useRef<() => void>(() => {});
  skipRef.current = () => {
    if (frameRef.current !== null) {
      cancelAnimationFrame(frameRef.current);
      frameRef.current = null;
    }
    setDisplay(target);
    if (phase === "counting") {
      setPhase(result === "BUST" ? "flashing" : "showing");
    } else if (phase === "flashing") {
      setPhase("showing");
    } else {
      // "showing" or "invalid" — dismiss now
      onComplete();
    }
  };

  useEffect(() => {
    const onKey = () => skipRef.current();
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, []);

  // ── Counting animation (0 → target) ──────────────────────────────────────
  useEffect(() => {
    if (phase !== "counting") return;

    const duration = 2000 + Math.random() * 2000;
    const wobbleAmp = 0.08 + Math.random() * 0.12;
    const wobbleHz = 2 + Math.random() * 3;
    const startTime = performance.now();

    const tick = (now: number) => {
      const elapsed = now - startTime;
      const raw = Math.min(elapsed / duration, 1);

      // Cubic ease-in-out
      const eased =
        raw < 0.5
          ? 4 * raw * raw * raw
          : 1 - Math.pow(-2 * raw + 2, 3) / 2;

      // Fading sinusoidal wobble
      const wobble =
        Math.sin(raw * Math.PI * wobbleHz) * wobbleAmp * (1 - raw);
      const progress = Math.max(0, Math.min(1, eased + wobble));

      setDisplay(Math.round(target * progress));

      if (raw < 1) {
        frameRef.current = requestAnimationFrame(tick);
      } else {
        setDisplay(target);
        setPhase(result === "BUST" ? "flashing" : "showing");
      }
    };

    frameRef.current = requestAnimationFrame(tick);

    return () => {
      if (frameRef.current !== null) cancelAnimationFrame(frameRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [phase]);

  // ── Hold final state then dismiss ────────────────────────────────────────
  useEffect(() => {
    if (phase === "flashing") {
      const t = setTimeout(() => setPhase("showing"), 500);
      return () => clearTimeout(t);
    }
    if (phase === "showing") {
      const t = setTimeout(onComplete, 500);
      return () => clearTimeout(t);
    }
    if (phase === "invalid") {
      const t = setTimeout(onComplete, 1500);
      return () => clearTimeout(t);
    }
  }, [phase, onComplete]);

  // ── Render ───────────────────────────────────────────────────────────────

  if (phase === "invalid") {
    return (
      <div
        className="fixed inset-0 z-[40] flex items-center justify-center bg-bg/90 backdrop-blur-sm animate-fade-in cursor-pointer"
        onClick={() => skipRef.current()}
      >
        <div className="text-center flex flex-col items-center gap-3 px-6">
          <div className="display-num text-danger" style={{ fontSize: "110px" }}>
            ✗
          </div>
          <div className="kicker text-danger text-[13px]">Invalid answer</div>
          {reason && (
            <div className="text-muted text-sm max-w-md leading-relaxed">
              {reason}
            </div>
          )}
          <div className="kicker text-muted/50 text-[10px] mt-2">Tap or press any key to skip</div>
        </div>
      </div>
    );
  }

  const isRedPhase = phase === "flashing" || (phase === "showing" && result === "BUST");
  const showBustText = phase === "showing" && result === "BUST";

  return (
    <div
      className="fixed inset-0 z-[40] flex items-center justify-center bg-bg/90 backdrop-blur-sm animate-fade-in cursor-pointer"
      onClick={() => skipRef.current()}
    >
      <div className="text-center flex flex-col items-center gap-2 px-6">
        <div
          className={`display-num transition-colors duration-150 ${
            isRedPhase ? "text-danger" : "text-ok"
          } ${phase === "flashing" ? "animate-pulse" : ""}`}
          style={{ fontSize: "clamp(120px, 24vw, 180px)" }}
        >
          {showBustText ? "BUST" : display}
        </div>
        {phase === "showing" && result !== "BUST" && (
          <div className="kicker text-[13px]">Points scored</div>
        )}
        {phase === "showing" && result === "BUST" && reason && (
          <div className="text-muted text-sm max-w-md leading-relaxed mt-2">
            {reason}
          </div>
        )}
        {phase === "counting" && (
          <div className="kicker text-muted/50 text-[10px] mt-4">Tap or press any key to skip</div>
        )}
      </div>
    </div>
  );
}
