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

function todayISO(): string {
  return new Date().toISOString().split("T")[0]!;
}

function key(categorySlug: string, date: string): string {
  return `${PREFIX}${categorySlug}_${date}`;
}

export function getDailyLock(categorySlug: string): DailyLockState | null {
  try {
    const raw = localStorage.getItem(key(categorySlug, todayISO()));
    if (!raw) return null;
    return JSON.parse(raw) as DailyLockState;
  } catch {
    return null;
  }
}

export function setDailyLockInProgress(categorySlug: string, gameId: string): void {
  try {
    localStorage.setItem(
      key(categorySlug, todayISO()),
      JSON.stringify({ state: "in_progress", gameId } satisfies DailyLockState),
    );
  } catch {
    /* storage unavailable — non-critical */
  }
}

export function setDailyLockCompleted(categorySlug: string, gameId: string): void {
  try {
    localStorage.setItem(
      key(categorySlug, todayISO()),
      JSON.stringify({ state: "completed", gameId } satisfies DailyLockState),
    );
  } catch {
    /* storage unavailable — non-critical */
  }
}

/** Remove lock entries from previous days to keep localStorage tidy. */
export function pruneStaleDailyLocks(): void {
  try {
    const today = todayISO();
    Object.keys(localStorage)
      .filter((k) => k.startsWith(PREFIX) && !k.includes(`_${today}`))
      .forEach((k) => localStorage.removeItem(k));
  } catch {
    /* ignore */
  }
}
