import { describe, it, expect, beforeEach } from "vitest";
import {
  saveGameState,
  loadSavedGameState,
  clearSavedGameState,
} from "@/hooks/useGamePersistence";

describe("useGamePersistence", () => {
  beforeEach(() => sessionStorage.clear());

  it("round-trips a daily game with its categorySlug", () => {
    saveGameState("g1", "Geography", "daily-challenge", "geography");
    expect(loadSavedGameState()).toEqual({
      gameId: "g1",
      label: "Geography",
      gameType: "daily-challenge",
      categorySlug: "geography",
    });
  });

  it("round-trips without a slug as categorySlug undefined", () => {
    saveGameState("g1", "Geography", "daily-challenge");
    expect(loadSavedGameState()).toEqual({
      gameId: "g1",
      label: "Geography",
      gameType: "daily-challenge",
      categorySlug: undefined,
    });
  });

  it("returns null after clearSavedGameState", () => {
    saveGameState("g1", "Geography", "daily-challenge", "geography");
    clearSavedGameState();
    expect(loadSavedGameState()).toBeNull();
  });
});
