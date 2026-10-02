import { describe, it, expect, vi, beforeEach } from "vitest";
import { startGame, loadSavedGame, type GameSpec } from "@/lib/gameStart";

const { mockApiFetch } = vi.hoisted(() => ({ mockApiFetch: vi.fn() }));
vi.mock("@/lib/api/client", () => ({ apiFetch: mockApiFetch }));

const ok = (body: unknown) => new Response(JSON.stringify(body), { status: 200 });
const game = (gameId: string) => ({ gameId, currentScore: 501, questionText: "Q", turnCount: 0, status: "IN_PROGRESS" });

const freeplay: GameSpec = {
  mode: "freeplay",
  categorySlug: "football",
  breadcrumb: ["Football", "Premier League", "Arsenal"],
  target: 301,
  filter: { scope: "club", league: "premier-league", club: "arsenal" },
};
const daily: GameSpec = { mode: "daily", categorySlug: "geography", breadcrumb: ["Geography"] };

const persist = (gameId: string, spec: GameSpec) =>
  sessionStorage.setItem("activeGameState", JSON.stringify({ gameId, spec }));

const calledUrls = () => mockApiFetch.mock.calls.map(([url]) => url);

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
});

describe("startGame", () => {
  it("persists the full Game Spec and sends the real filter + target", async () => {
    mockApiFetch.mockResolvedValue(ok(game("g1")));

    await startGame(freeplay);

    expect(loadSavedGame()).toEqual({ gameId: "g1", spec: freeplay });
    const body = JSON.parse(mockApiFetch.mock.calls[0][1].body);
    expect(body).toEqual({ categorySlug: "football", startingScore: 301, footballFilter: freeplay.filter });
  });

  it("abandons a persisted Free Play game via the freeplay endpoint", async () => {
    persist("old", freeplay);
    mockApiFetch.mockResolvedValue(ok(game("new")));

    await startGame(daily);

    expect(calledUrls()).toContain("/api/freeplay/games/old/abandon");
  });

  it("never abandons a persisted daily", async () => {
    persist("old-daily", daily);
    mockApiFetch.mockResolvedValue(ok(game("new")));

    await startGame(freeplay);

    expect(calledUrls().some((u) => u.includes("abandon"))).toBe(false);
  });

  it("surfaces the server's error and keeps the previous game", async () => {
    persist("old", freeplay);
    mockApiFetch.mockResolvedValue(
      new Response(JSON.stringify({ error: "Already played today" }), { status: 409 }),
    );

    await expect(startGame(daily)).rejects.toThrow("Already played today");
    expect(calledUrls().some((u) => u.includes("abandon"))).toBe(false);
    expect(loadSavedGame()?.gameId).toBe("old");
  });
});

describe("loadSavedGame", () => {
  it("drops pre-GameSpec entries", () => {
    sessionStorage.setItem(
      "activeGameState",
      JSON.stringify({ gameId: "g1", label: "Football", gameType: "freeplay" }),
    );
    expect(loadSavedGame()).toBeNull();
  });
});
