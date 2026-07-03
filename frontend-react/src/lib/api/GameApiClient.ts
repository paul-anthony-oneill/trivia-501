import { apiFetch } from "@/lib/api/client";
import type { FootballFilter } from "@/lib/api/footballApi";
import type { GameStateResponse, SubmitAnswerResponse } from "@/hooks/useGameLoop.types";

/**
 * Typed API facade for game endpoints, matching the {@code AdminApiClient}
 * pattern. Extracted from {@code useGameLoop} so the hook only coordinates.
 */
class GameApiClient {
  private basePath(gameType: "freeplay" | "daily-challenge"): string {
    return gameType === "daily-challenge" ? "/api/daily-challenge" : "/api/freeplay";
  }

  private async request<T>(
    endpoint: string,
    options: RequestInit = {},
  ): Promise<T> {
    const response = await apiFetch(endpoint, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        ...options.headers,
      },
    });

    if (!response.ok) {
      const text = await response.text().catch(() => "");
      let msg = "Request failed";
      try {
        const parsed = JSON.parse(text);
        msg = parsed.error || parsed.message || text;
      } catch {
        msg = text || msg;
      }
      throw new Error(msg);
    }

    return response.json();
  }

  async startFreePlay(
    categorySlug: string,
    targetScore?: number,
    footballFilter?: FootballFilter,
  ): Promise<GameStateResponse> {
    // ponytail: compound slugs (football:premier-league:league:goals) are for
    // UI nav state; the backend only knows simple category slugs like "football"
    const baseCategory = categorySlug.includes(":") ? categorySlug.split(":")[0] : categorySlug;
    return this.request<GameStateResponse>("/api/freeplay/start", {
      method: "POST",
      body: JSON.stringify({
        categorySlug: baseCategory,
        startingScore: targetScore,
        footballFilter,
      }),
    });
  }

  async startDailyChallenge(categorySlug: string): Promise<GameStateResponse> {
    return this.request<GameStateResponse>(
      `/api/daily-challenge/${encodeURIComponent(categorySlug)}/start`,
      { method: "POST" },
    );
  }

  async submitAnswer(
    gameId: string,
    answer: string,
    entityId: string | null,
    gameType: "freeplay" | "daily-challenge",
  ): Promise<SubmitAnswerResponse> {
    return this.request<SubmitAnswerResponse>(
      `${this.basePath(gameType)}/games/${gameId}/submit`,
      {
        method: "POST",
        body: JSON.stringify({ answer: answer.trim(), entityId }),
      },
    );
  }

  /** Fire-and-forget abandon — server is idempotent. */
  abandonGame(gameId: string, gameType: "freeplay" | "daily-challenge"): void {
    apiFetch(`${this.basePath(gameType)}/games/${gameId}/abandon`, { method: "POST" }).catch(
      () => {},
    );
  }

  async getGameState(
    gameId: string,
    gameType: "freeplay" | "daily-challenge",
  ): Promise<GameStateResponse> {
    return this.request<GameStateResponse>(`${this.basePath(gameType)}/games/${gameId}`);
  }

  async getActiveGame(): Promise<GameStateResponse> {
    return this.request<GameStateResponse>("/api/freeplay/games/active");
  }
}

export const gameApiClient = new GameApiClient();
