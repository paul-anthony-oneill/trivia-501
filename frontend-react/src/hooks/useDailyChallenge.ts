"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import { apiFetch } from "@/lib/api/client";
import {
  getDailyLock,
  pruneStaleDailyLocks,
  type DailyLockState,
} from "@/lib/dailyLock";

export interface CategoryChallenge {
  categorySlug: string;
  categoryName: string;
  startingScore: number;
  questionText: string;
  hasChallenge: boolean;
  lockState: DailyLockState | null;
}

export interface DailyChallengeState {
  date: string | null;
  challenges: CategoryChallenge[];
  loading: boolean;
  error: string | null;
}

interface CachedStatus {
  date: string;
  challenges: Omit<CategoryChallenge, "lockState">[];
}

const CACHE_KEY = "dc_status_cache";

function getCachedStatus(): CachedStatus | null {
  try {
    const raw = sessionStorage.getItem(CACHE_KEY);
    if (!raw) return null;
    const cached = JSON.parse(raw) as CachedStatus;
    if (cached.date === new Date().toISOString().slice(0, 10)) return cached;
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

export function useDailyChallenge(): DailyChallengeState & { refresh: () => void } {
  const cached = getCachedStatus();

  const [challenges, setChallenges] = useState<CategoryChallenge[]>(() => {
    if (cached) {
      pruneStaleDailyLocks();
      return cached.challenges.map((c) => ({
        ...c,
        lockState: getDailyLock(c.categorySlug),
      }));
    }
    return [];
  });
  const [date, setDate] = useState<string | null>(cached?.date ?? null);
  const [loading, setLoading] = useState(!cached);
  const [error, setError] = useState<string | null>(null);
  const reqId = useRef(0);
  const hasData = useRef(!!cached);

  const fetchStatus = useCallback((signal?: AbortSignal) => {
    const id = ++reqId.current;
    if (!hasData.current) setLoading(true);
    setError(null);
    pruneStaleDailyLocks();

    apiFetch("/api/daily-challenge/status")
      .then(async (res) => {
        if (signal?.aborted || id !== reqId.current) return;
        if (!res.ok) throw new Error("Failed to fetch daily challenges");
        return res.json();
      })
      .then((data) => {
        if (signal?.aborted || id !== reqId.current) return;
        const newDate: string = data.date ?? new Date().toISOString().slice(0, 10);
        const raw: Omit<CategoryChallenge, "lockState">[] =
          data.challenges ?? [];
        setDate(newDate);
        setChallenges(
          raw.map((c) => ({ ...c, lockState: getDailyLock(c.categorySlug) })),
        );
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
  }, []);

  return { date, challenges, loading, error, refresh: fetchStatus };
}
