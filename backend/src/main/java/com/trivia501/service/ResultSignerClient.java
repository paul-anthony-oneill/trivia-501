package com.trivia501.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Client interface for the go-signer microservice.
 *
 * <p>Called once when a game ends in CHECKOUT to obtain a signed token that makes
 * share links cryptographically verifiable. Callers must treat a missing return
 * value as non-fatal — the game completes regardless of whether signing succeeds.
 */
public interface ResultSignerClient {

    /**
     * Request a signed token for a completed game.
     *
     * @param gameId      the completed game's UUID
     * @param playerId    the winning player's UUID
     * @param finalScore  the player's final score (typically -10 to 0)
     * @param completedAt the game's completion timestamp
     * @return the signed token string, or empty if the service was unavailable
     */
    Optional<String> sign(UUID gameId, UUID playerId, int finalScore, LocalDateTime completedAt);
}
