"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import { useToast } from "@/context/ToastContext";
import { gameApiClient, type DailyStatus } from "@/lib/api/GameApiClient";
import type { GameSpec } from "@/lib/gameStart";
import { buildShareText } from "@/utils/share";
import {
  getDailyLock,
  pruneStaleDailyLocks,
  todayUTC,
  type DailyLockState,
} from "@/lib/dailyLock";

/**
 * The Daily module — everything pages need about today's Daily Challenges:
 * the status list (cached per UTC day), one category's status, starting a
 * daily behind the one-attempt confirm, and the player's result text.
 */

export interface CategoryChallenge extends DailyStatus {
  lockState: DailyLockState | null;
}

export interface DailyChallengeState {
  date: string | null;
  challenges: CategoryChallenge[];
  loading: boolean;
  error: string | null;
}

// ── Status cache (per UTC day, per tab) ─────────────────────────────────────

interface CachedStatus {
  date: string;
  challenges: DailyStatus[];
}

const CACHE_KEY = "dc_status_cache";

function getCachedStatus(): CachedStatus | null {
  try {
    const raw = sessionStorage.getItem(CACHE_KEY);
    if (!raw) return null;
    const cached = JSON.parse(raw) as CachedStatus;
    if (cached.date === todayUTC()) return cached;
  } catch {
    // Corrupt cache — ignore
  }
  return null;
}

function setCachedStatus(status: CachedStatus): void {
  try {
    sessionStorage.setItem(CACHE_KEY, JSON.stringify(status));
  } catch {
    // Storage full or unavailable — ignore
  }
}

function withLocks(list: DailyStatus[]): CategoryChallenge[] {
  pruneStaleDailyLocks();
  return list.map((c) => ({ ...c, lockState: getDailyLock(c.categorySlug) }));
}

// ── All of today's challenges ───────────────────────────────────────────────

export function useDailyChallenge(): DailyChallengeState & { refresh: () => void } {
  const [challenges, setChallenges] = useState<CategoryChallenge[]>(() => {
    const cached = getCachedStatus();
    return cached ? withLocks(cached.challenges) : [];
  });
  const [date, setDate] = useState<string | null>(() => getCachedStatus()?.date ?? null);
  const [loading, setLoading] = useState(() => !getCachedStatus());
  const [error, setError] = useState<string | null>(null);
  const reqId = useRef(0);
  const hasData = useRef(challenges.length > 0);

  const fetchStatus = useCallback((signal?: AbortSignal) => {
    const id = ++reqId.current;
    if (!hasData.current) setLoading(true);
    setError(null);

    gameApiClient
      .getDailyStatuses()
      .then((data) => {
        if (signal?.aborted || id !== reqId.current) return;
        const newDate = data.date ?? todayUTC();
        const raw = data.challenges ?? [];
        setDate(newDate);
        setChallenges(withLocks(raw));
        setLoading(false);
        hasData.current = true;
        setCachedStatus({ date: newDate, challenges: raw });
      })
      .catch((err) => {
        if (signal?.aborted || id !== reqId.current) return;
        if (!hasData.current) {
          setError(err.message || "Error fetching daily challenges");
        }
        setLoading(false);
      });
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    fetchStatus(controller.signal);
    return () => controller.abort();
  }, [fetchStatus]);

  return { date, challenges, loading, error, refresh: fetchStatus };
}

// ── One category (share-link landing page) ──────────────────────────────────

/** Cached status list if this tab has it, otherwise the single-category endpoint. */
export function useDailyStatus(categorySlug: string) {
  const [status, setStatus] = useState<DailyStatus | null>(null);
  const [lock, setLock] = useState<DailyLockState | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    pruneStaleDailyLocks();
    setLock(getDailyLock(categorySlug));

    const cached = getCachedStatus()?.challenges.find(
      (c) => c.categorySlug === categorySlug,
    );
    if (cached) {
      setStatus(cached);
      setLoading(false);
      return;
    }

    let live = true;
    gameApiClient
      .getDailyStatus(categorySlug)
      .then((s) => live && setStatus(s))
      .catch(() => live && setError("No challenge found for this category today"))
      .finally(() => live && setLoading(false));
    return () => {
      live = false;
    };
  }, [categorySlug]);

  return { status, lock, loading, error };
}

// ── Starting a daily ────────────────────────────────────────────────────────

/**
 * `play(slug, label)` resumes an in-progress daily straight away and asks the
 * one-attempt question otherwise. Render `<ConfirmDialog {...confirmProps} />`.
 */
export function useDailyStart(start: (spec: GameSpec) => Promise<void>) {
  const { addToast } = useToast();
  const [starting, setStarting] = useState<string | null>(null);
  const [pending, setPending] = useState<{ slug: string; label: string } | null>(null);

  async function begin(slug: string, label: string) {
    if (starting) return;
    setStarting(slug);
    try {
      await start({ mode: "daily", categorySlug: slug, breadcrumb: [label] });
    } catch (err) {
      addToast((err as Error).message || "Error starting daily challenge", "error");
    } finally {
      setStarting(null);
    }
  }

  function play(slug: string, label: string) {
    if (getDailyLock(slug)?.state === "in_progress") begin(slug, label);
    else setPending({ slug, label });
  }

  const confirmProps = {
    open: pending !== null,
    title: `Play today's ${pending?.label} challenge?`,
    message: "You only get one attempt per day. Once you start, this is your shot.",
    confirmText: "Let's go",
    cancelText: "Not yet",
    type: "info" as const,
    onConfirm: () => {
      if (pending) begin(pending.slug, pending.label);
      setPending(null);
    },
    onCancel: () => setPending(null),
  };

  return { starting, play, confirmProps };
}

// ── Result ──────────────────────────────────────────────────────────────────

/** The player's emoji-grid result for a finished daily game. */
export async function dailyResultText(gameId: string): Promise<string> {
  return buildShareText(await gameApiClient.getShareData(gameId), window.location.origin);
}
