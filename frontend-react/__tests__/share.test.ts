import { describe, it, expect } from "vitest";
import { buildShareText, type ShareData } from "@/utils/share";

function makeData(overrides: Partial<ShareData> = {}): ShareData {
  return {
    moveEmojis: [],
    challengeDate: "2026-06-09",
    categoryName: "Football",
    startingScore: 501,
    finalScore: 0,
    turnCount: 5,
    categorySlug: "football",
    ...overrides,
  };
}

const origin = "https://trivia501.example.com";

// ── Emoji mapping ─────────────────────────────────────────────────────────

describe("buildShareText — emoji mapping", () => {
  it("maps VALID → 🟩", () => {
    const text = buildShareText(
      makeData({ moveEmojis: ["VALID"] }),
      origin,
    );
    expect(text).toContain("🟩");
  });

  it("maps BUST → 🟥", () => {
    const text = buildShareText(
      makeData({ moveEmojis: ["BUST"] }),
      origin,
    );
    expect(text).toContain("🟥");
  });

  it("maps INVALID → ⬜", () => {
    const text = buildShareText(
      makeData({ moveEmojis: ["INVALID"] }),
      origin,
    );
    expect(text).toContain("⬜");
  });

  it("maps CHECKOUT → 🎯", () => {
    const text = buildShareText(
      makeData({ moveEmojis: ["CHECKOUT"] }),
      origin,
    );
    expect(text).toContain("🎯");
  });

  it("maps unknown emoji types → ⬜ (fallback)", () => {
    const text = buildShareText(
      makeData({ moveEmojis: ["UNKNOWN_TYPE" as string] }),
      origin,
    );
    expect(text).toContain("⬜");
  });

  it("produces empty emoji line for empty moves array", () => {
    const text = buildShareText(makeData({ moveEmojis: [] }), origin);
    // After the "Target: 501" line, there should be a blank then the emoji line (empty)
    const lines = text.split("\n");
    const emojiLine = lines[3];
    expect(emojiLine).toBe("");
  });

  it("combines multiple emojis into one line", () => {
    const text = buildShareText(
      makeData({ moveEmojis: ["VALID", "VALID", "BUST", "CHECKOUT"] }),
      origin,
    );
    expect(text).toContain("🟩🟩🟥🎯");
  });

  it("handles a classic 6-turn checkout", () => {
    const text = buildShareText(
      makeData({
        moveEmojis: ["VALID", "VALID", "BUST", "VALID", "VALID", "CHECKOUT"],
        turnCount: 6,
      }),
      origin,
    );
    expect(text).toContain("🟩🟩🟥🟩🟩🎯");
    expect(text).toContain("6 turns");
  });
});

// ── Score formatting ───────────────────────────────────────────────────────

describe("buildShareText — score formatting", () => {
  it('shows "000" for finalScore of 0', () => {
    const text = buildShareText(makeData({ finalScore: 0 }), origin);
    expect(text).toContain("Score: 000");
  });

  it("zero-pads single-digit scores", () => {
    const text = buildShareText(makeData({ finalScore: 7 }), origin);
    expect(text).toContain("Score: 007");
  });

  it("zero-pads double-digit scores", () => {
    const text = buildShareText(makeData({ finalScore: 42 }), origin);
    expect(text).toContain("Score: 042");
  });

  it("does not pad triple-digit scores", () => {
    const text = buildShareText(makeData({ finalScore: 167 }), origin);
    expect(text).toContain("Score: 167");
  });

  it('shows "000" for negative scores (bust-out below 0)', () => {
    const text = buildShareText(makeData({ finalScore: -10 }), origin);
    expect(text).toContain("Score: 000");
  });
});

// ── Date formatting ────────────────────────────────────────────────────────

describe("buildShareText — date formatting", () => {
  it('shows "Today" when challengeDate is null', () => {
    const text = buildShareText(
      makeData({ challengeDate: null }),
      origin,
    );
    expect(text).toContain("Today");
  });

  it("formats date in en-GB style (day month year)", () => {
    const text = buildShareText(
      makeData({ challengeDate: "2026-06-09" }),
      origin,
    );
    // en-GB: "9 Jun 2026"
    expect(text).toMatch(/9 Jun 2026/);
  });
});

// ── Header & footer ────────────────────────────────────────────────────────

describe("buildShareText — header and footer", () => {
  it("includes category name in uppercase", () => {
    const text = buildShareText(
      makeData({ categoryName: "Geography" }),
      origin,
    );
    expect(text).toContain("🎯 TRIVIA 501 — GEOGRAPHY");
  });

  it("includes starting score in header", () => {
    const text = buildShareText(makeData({ startingScore: 301 }), origin);
    expect(text).toContain("Target: 301");
  });

  it("includes deep-link URL with origin and category slug", () => {
    const text = buildShareText(makeData({ categorySlug: "film" }), origin);
    expect(text).toContain(`${origin}/daily/film`);
  });

  it("includes turn count", () => {
    const text = buildShareText(makeData({ turnCount: 12 }), origin);
    expect(text).toContain("12 turns");
  });
});

// ── Structure / integration ────────────────────────────────────────────────

describe("buildShareText — full output structure", () => {
  it("has exactly 7 lines", () => {
    const text = buildShareText(
      makeData({
        moveEmojis: ["VALID", "BUST", "CHECKOUT"],
        finalScore: 32,
        turnCount: 3,
      }),
      origin,
    );
    const lines = text.split("\n");
    expect(lines).toHaveLength(7);
  });

  it("matches snapshot for a complete game", () => {
    const text = buildShareText(
      makeData({
        moveEmojis: ["VALID", "VALID", "BUST", "VALID", "VALID", "CHECKOUT"],
        challengeDate: "2026-06-09",
        categoryName: "Football",
        startingScore: 501,
        finalScore: 0,
        turnCount: 6,
        categorySlug: "football",
      }),
      origin,
    );
    expect(text).toBe(
      [
        "🎯 TRIVIA 501 — FOOTBALL",
        "9 Jun 2026 — Target: 501",
        "",
        "🟩🟩🟥🟩🟩🎯",
        "Score: 000 | 6 turns",
        "",
        "https://trivia501.example.com/daily/football",
      ].join("\n"),
    );
  });
});
