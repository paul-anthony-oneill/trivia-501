import { apiFetch } from "@/lib/api/client";
import type { FootballFilter } from "@/lib/api/footballApi";
import type { GameStateResponse, SubmitAnswerResponse } from "@/hooks/useGameLoop.types";

/**
 * Typed API facade for game endpoints, matching the {@code AdminApiClient}
 * pattern. Extracted from {@code useGameLoop} so the hook only coordinates.
 */
class GameApiClient {
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
    return this.request<GameStateResponse>("/api/freeplay/start", {
      method: "POST",
      body: JSON.stringify({
        categorySlug,
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
    const base =
      gameType === "daily-challenge" ? "/api/daily-challenge" : "/api/freeplay";
    return this.request<SubmitAnswerResponse>(
      `${base}/games/${gameId}/submit`,
      {
        method: "POST",
        body: JSON.stringify({ answer: answer.trim(), entityId }),
      },
    );
  }

  /** Fire-and-forget abandon — server is idempotent. */
  abandonGame(gameId: string, gameType: "freeplay" | "daily-challenge"): void {
    const base =
      gameType === "daily-challenge" ? "/api/daily-challenge" : "/api/freeplay";
    apiFetch(`${base}/games/${gameId}/abandon`, { method: "POST" }).catch(
      () => {},
    );
  }

  async getGameState(
    gameId: string,
    gameType: "freeplay" | "daily-challenge",
  ): Promise<GameStateResponse> {
    const base =
      gameType === "daily-challenge" ? "/api/daily-challenge" : "/api/freeplay";
    return this.request<GameStateResponse>(`${base}/games/${gameId}`);
  }

  async getActiveGame(): Promise<GameStateResponse> {
    return this.request<GameStateResponse>("/api/freeplay/games/active");
  }
}

export const gameApiClient = new GameApiClient();
