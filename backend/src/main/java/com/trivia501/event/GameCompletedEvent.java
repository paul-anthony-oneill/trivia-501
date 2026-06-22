package com.trivia501.event;

import java.util.UUID;

/**
 * Published by {@link com.trivia501.service.GameService} when a game reaches
 * a terminal status (CHECKOUT, bust-out, or forfeit).
 * {@link com.trivia501.service.MatchService} listens for this event to update
 * match-level state, replacing the former direct call that created a circular
 * dependency.
 */
public record GameCompletedEvent(UUID gameId, UUID matchId, UUID winnerId, boolean isCheckout) {}
