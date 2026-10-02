/**
 * localStorage-based tracking for daily challenge state.
 *
 * A daily is locked as "in_progress" on the first dart thrown, and updated to
 * "completed" when the player checks out or busts out. The lock key includes the
 * date so it resets automatically at midnight — no explicit reset needed.
 *
 * The backend is the authoritative gate (POST /start returns 409 for completed
 * games). This utility drives UI state only.
 */

const PREFIX = "daily_lock_";

export type DailyLockState =
  | { state: "in_progress"; gameId: string }
  | { state: "completed"; gameId: string };

/** Today as YYYY-MM-DD in UTC — the day boundary for Daily Challenges. */
export function todayUTC(): string {
  return new Date().toISOString().split("T")[0]!;
}

function key(categorySlug: string, date: string): string {
  return `${PREFIX}${categorySlug}_${date}`;
}

export function getDailyLock(categorySlug: string): DailyLockState | null {
  try {
    const raw = localStorage.getItem(key(categorySlug, todayUTC()));
    if (!raw) return null;
    return JSON.parse(raw) as DailyLockState;
  } catch {
    return null;
  }
}

function setDailyLockInProgress(categorySlug: string, gameId: string): void {
  try {
    localStorage.setItem(
      key(categorySlug, todayUTC()),
      JSON.stringify({ state: "in_progress", gameId } satisfies DailyLockState),
    );
  } catch {
    /* storage unavailable — non-critical */
  }
}

function setDailyLockCompleted(categorySlug: string, gameId: string): void {
  try {
    localStorage.setItem(
      key(categorySlug, todayUTC()),
      JSON.stringify({ state: "completed", gameId } satisfies DailyLockState),
    );
  } catch {
    /* storage unavailable — non-critical */
  }
}

/**
 * The one Daily Lock rule: a finished game completes the lock, a game with a
 * dart thrown marks it in progress, and a completed lock is never downgraded.
 */
export function recordDailyProgress(
  categorySlug: string,
  gameId: string,
  game: { completed: boolean; turnCount: number },
): void {
  if (game.completed) setDailyLockCompleted(categorySlug, gameId);
  else if (game.turnCount > 0 && getDailyLock(categorySlug)?.state !== "completed") {
    setDailyLockInProgress(categorySlug, gameId);
  }
}

/** Remove lock entries from previous days to keep localStorage tidy. */
export function pruneStaleDailyLocks(): void {
  try {
    const today = todayUTC();
    Object.keys(localStorage)
      .filter((k) => k.startsWith(PREFIX) && !k.includes(`_${today}`))
      .forEach((k) => localStorage.removeItem(k));
  } catch {
    /* ignore */
  }
}
