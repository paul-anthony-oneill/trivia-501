import { describe, it, expect, vi, afterEach } from "vitest";
import { render, act } from "@testing-library/react";
import AnimatedScorePopup from "@/components/game/AnimatedScorePopup";

// eslint-disable-next-line @typescript-eslint/no-explicit-any
window.matchMedia = window.matchMedia || ((() => ({ matches: false, addListener() {}, removeListener() {}, addEventListener() {}, removeEventListener() {} })) as any);

afterEach(() => vi.useRealTimers());

describe("AnimatedScorePopup — dismiss timer", () => {
  it("auto-dismisses INVALID after 1500ms even when the parent re-renders every second with a new onComplete", () => {
    vi.useFakeTimers();
    const calls: number[] = [];
    // A new function identity each render, like useGameLoop's handlePopupComplete
    const make = (n: number) => () => calls.push(n);

    const { rerender } = render(<AnimatedScorePopup scoreValue={0} result="INVALID" onComplete={make(0)} />);
    act(() => { vi.advanceTimersByTime(1000); });
    rerender(<AnimatedScorePopup scoreValue={0} result="INVALID" onComplete={make(1)} />);
    act(() => { vi.advanceTimersByTime(600); });

    expect(calls).toHaveLength(1);          // fired once, at ~1500ms from mount
    expect(calls[0]).toBe(1);               // and called the LATEST callback
  });
});
