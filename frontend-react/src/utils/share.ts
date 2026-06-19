export interface ShareData {
  moveEmojis: string[];
  challengeDate: string | null;
  categoryName: string;
  startingScore: number;
  finalScore: number;
  turnCount: number;
  categorySlug: string;
}

const emojiMap: Record<string, string> = {
  VALID: "🟩",
  BUST: "🟥",
  INVALID: "⬜",
  CHECKOUT: "🎯",
};

const MONTHS = [
  "Jan", "Feb", "Mar", "Apr", "May", "Jun",
  "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
];

/** Parses "YYYY-MM-DD" as local date (avoids UTC timezone shift). */
function formatDateGB(dateStr: string): string {
  const [year, month, day] = dateStr.split("-").map(Number);
  if (!year || !month || !day) return dateStr;
  return `${day} ${MONTHS[month - 1]} ${year}`;
}

/**
 * Builds a Wordle-style share text from a daily challenge share API response.
 * Pure function — no side effects, no DOM access.
 */
export function buildShareText(data: ShareData, origin: string): string {
  const emojiLine = data.moveEmojis
    .map((e: string) => emojiMap[e] ?? "⬜")
    .join("");

  const dateStr = data.challengeDate
    ? formatDateGB(data.challengeDate)
    : "Today";

  const paddedScore =
    data.finalScore <= 0
      ? "000"
      : String(data.finalScore).padStart(3, "0");

  return [
    `🎯 TRIVIA 501 — ${data.categoryName.toUpperCase()}`,
    `${dateStr} — Target: ${data.startingScore}`,
    "",
    emojiLine,
    `Score: ${paddedScore} | ${data.turnCount} turns`,
    "",
    `${origin}/daily/${data.categorySlug}`,
  ].join("\n");
}
