import { describe, it, expect, beforeEach, vi } from "vitest";
import { getDailyLock, recordDailyProgress } from "@/lib/dailyLock";

describe("recordDailyProgress", () => {
  // Node's own (file-less) localStorage shadows jsdom's, so stub a Map-backed one.
  beforeEach(() => {
    const store = new Map<string, string>();
    vi.stubGlobal("localStorage", {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => void store.set(k, v),
    });
  });

  it("does nothing before the first dart", () => {
    recordDailyProgress("football", "g1", { completed: false, turnCount: 0 });
    expect(getDailyLock("football")).toBeNull();
  });

  it("marks in progress once a dart is thrown", () => {
    recordDailyProgress("football", "g1", { completed: false, turnCount: 1 });
    expect(getDailyLock("football")).toEqual({ state: "in_progress", gameId: "g1" });
  });

  it("completes the lock when the game finishes", () => {
    recordDailyProgress("football", "g1", { completed: false, turnCount: 1 });
    recordDailyProgress("football", "g1", { completed: true, turnCount: 2 });
    expect(getDailyLock("football")).toEqual({ state: "completed", gameId: "g1" });
  });

  it("never downgrades a completed lock", () => {
    recordDailyProgress("football", "g1", { completed: true, turnCount: 3 });
    recordDailyProgress("football", "g1", { completed: false, turnCount: 1 });
    expect(getDailyLock("football")?.state).toBe("completed");
  });
});
