package com.trivia501.engine;

import com.trivia501.model.Game;
import com.trivia501.model.GameMove;

import java.util.UUID;

/**
 * Immutable record describing the outcome of a single game-state transition.
 *
 * <p>Produced by {@link GameStateMachine} after processing a move or timeout event.
 * Consumed by {@link com.trivia501.service.GameService}, which is the only place
 * that reads this record and writes the resulting state back to the database.
 *
 * <h3>Design contract</h3>
 * <ul>
 *   <li>All fields reflect what the game state <em>should be after</em> the transition.</li>
 *   <li>Score fields carry the <em>active player's</em> score after this move.</li>
 *   <li>{@code nextTurnPlayerId} is {@code null} when the game is over (saves a null-check
 *       on the caller — it simply skips the turn assignment).</li>
 *   <li>{@code winnerId} is {@code null} while the game is still in progress.</li>
 * </ul>
 *
 * @param moveResult                classification of the move (VALID, BUST, INVALID, CHECKOUT, TIMEOUT)
 * @param scoreAfter                the active player's score after this transition
 * @param turnAdvanced              {@code true} if the turn counter should increment
 * @param nextTurnPlayerId          who plays next; {@code null} when the game is over
 * @param nextGameStatus            the game status to persist
 * @param winnerId                  set when the game concludes; {@code null} otherwise
 * @param nextTimerSeconds          timer duration (seconds) for the next turn
 * @param playerConsecutiveTimeouts updated timeout count for Player 1
 */
public record GameTransition(
        GameMove.MoveResult moveResult,
        int scoreAfter,
        boolean turnAdvanced,
        UUID nextTurnPlayerId,
        Game.GameStatus nextGameStatus,
        UUID winnerId,
        int nextTimerSeconds,
        int playerConsecutiveTimeouts
) {}
