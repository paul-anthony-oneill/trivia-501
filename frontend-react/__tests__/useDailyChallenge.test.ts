import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { useDailyStatus, useDailyStart } from "@/hooks/useDailyChallenge";
import { todayUTC } from "@/lib/dailyLock";

const { mockGetDailyStatus, mockAddToast } = vi.hoisted(() => ({
  mockGetDailyStatus: vi.fn(),
  mockAddToast: vi.fn(),
}));

vi.mock("@/lib/api/GameApiClient", () => ({
  gameApiClient: { getDailyStatus: mockGetDailyStatus },
}));
vi.mock("@/context/ToastContext", () => ({
  useToast: () => ({ addToast: mockAddToast }),
}));

const football = {
  categorySlug: "football",
  categoryName: "Football",
  startingScore: 141,
  questionText: "Goals + Appearances in Bundesliga",
  hasChallenge: true,
};

// Node's own (file-less) Web Storage shadows jsdom's, so stub Map-backed ones.
function memoryStorage() {
  const store = new Map<string, string>();
  return {
    getItem: (k: string) => store.get(k) ?? null,
    setItem: (k: string, v: string) => void store.set(k, v),
    removeItem: (k: string) => void store.delete(k),
    keys: () => [...store.keys()],
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.stubGlobal("sessionStorage", memoryStorage());
  vi.stubGlobal("localStorage", memoryStorage());
});

describe("useDailyStatus", () => {
  it("uses today's cached status list without a request", async () => {
    sessionStorage.setItem(
      "dc_status_cache",
      JSON.stringify({ date: todayUTC(), challenges: [football] }),
    );
    const { result } = renderHook(() => useDailyStatus("football"));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.status).toEqual(football);
    expect(mockGetDailyStatus).not.toHaveBeenCalled();
  });

  it("falls back to the single-category endpoint on a cold cache", async () => {
    mockGetDailyStatus.mockResolvedValue(football);
    const { result } = renderHook(() => useDailyStatus("football"));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(mockGetDailyStatus).toHaveBeenCalledWith("football");
    expect(result.current.status).toEqual(football);
  });

  it("reports an error when the category has no challenge", async () => {
    mockGetDailyStatus.mockRejectedValue(new Error("404"));
    const { result } = renderHook(() => useDailyStatus("film"));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toMatch(/No challenge found/);
  });
});

describe("useDailyStart", () => {
  it("asks the one-attempt question before a fresh daily", async () => {
    const start = vi.fn().mockResolvedValue(undefined);
    const { result } = renderHook(() => useDailyStart(start));

    act(() => result.current.play("football", "Football"));
    expect(start).not.toHaveBeenCalled();
    expect(result.current.confirmProps.open).toBe(true);

    await act(async () => result.current.confirmProps.onConfirm());
    expect(start).toHaveBeenCalledWith({
      mode: "daily",
      categorySlug: "football",
      breadcrumb: ["Football"],
    });
  });

  it("resumes an in-progress daily without asking", async () => {
    localStorage.setItem(
      `daily_lock_football_${todayUTC()}`,
      JSON.stringify({ state: "in_progress", gameId: "g1" }),
    );
    const start = vi.fn().mockResolvedValue(undefined);
    const { result } = renderHook(() => useDailyStart(start));

    await act(async () => result.current.play("football", "Football"));
    expect(start).toHaveBeenCalledOnce();
    expect(result.current.confirmProps.open).toBe(false);
  });

  it("toasts when the start fails", async () => {
    const start = vi.fn().mockRejectedValue(new Error("Already played today"));
    const { result } = renderHook(() => useDailyStart(start));

    act(() => result.current.play("football", "Football"));
    await act(async () => result.current.confirmProps.onConfirm());
    expect(mockAddToast).toHaveBeenCalledWith("Already played today", "error");
    expect(result.current.starting).toBeNull();
  });
});
