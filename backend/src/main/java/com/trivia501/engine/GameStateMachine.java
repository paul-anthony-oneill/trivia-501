package com.trivia501.engine;

import com.trivia501.model.Game;
import com.trivia501.model.GameMove;
import com.trivia501.model.Match;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Pure game-state-transition coordinator for solo play.
 *
 * <p>This class owns all Football 501 turn-machine rules and produces an immutable
 * {@link GameTransition} that describes exactly what should change. It has
 * <strong>no repository dependencies</strong> — all input is loaded by
 * {@link com.trivia501.service.GameService}, which is also responsible for
 * persisting the returned transition.
 *
 * <h3>State machine rules (solo)</h3>
 * <ul>
 *   <li><b>INVALID</b>  — same player retries; nothing changes (timer keeps running)</li>
 *   <li><b>BUST</b>     — no score change; same player retries</li>
 *   <li><b>VALID</b>    — score deducted; timeout counter resets; same player continues</li>
 *   <li><b>CHECKOUT</b> — immediate win</li>
 *   <li><b>TIMEOUT</b>  — increments consecutive counter; reduces timer;
 *                         forfeits as bust-out (no winner) at threshold</li>
 * </ul>
 *
 * <p>Multiplayer rules (close-finish, turn alternation, opponent forfeit) are
 * documented in {@code docs/GAME_RULES.md} for reference. This engine is
 * intentionally solo-only — when multiplayer returns, it will be rebuilt
 * from a real product spec.
 */
@Component
@Slf4j
public class GameStateMachine {

    /** Default turn timer in seconds. */
    public static final int DEFAULT_TIMER = 45;

    /** Timer after the 1st consecutive timeout. */
    public static final int REDUCED_TIMER_1 = 30;

    /** Timer after the 2nd consecutive timeout. */
    public static final int REDUCED_TIMER_2 = 15;

    /** Number of consecutive timeouts before the player forfeits. */
    public static final int FORFEIT_TIMEOUT_THRESHOLD = 3;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Process a submitted answer and compute the resulting {@link GameTransition}.
     *
     * @param game         current game entity (read-only in this call)
     * @param match        current match entity
     * @param playerId     the player who submitted the answer
     * @param answerResult the evaluation produced by {@link AnswerEvaluator}
     * @return an immutable transition descriptor; never {@code null}
     */
    public GameTransition onMoveSubmitted(
            Game game,
            Match match,
            UUID playerId,
            AnswerResult answerResult
    ) {
        GameMove.MoveResult moveResult = classifyMove(answerResult);
        int currentScore = getPlayerScore(game, match, playerId);

        // ── default values (for INVALID: nothing changes) ──────────────────
        int scoreAfter            = currentScore;
        boolean turnAdvanced      = false;
        UUID nextTurnPlayerId     = game.getCurrentTurnPlayerId();
        Game.GameStatus nextStatus = game.getStatus();
        UUID winnerId              = game.getWinnerId();
        int nextTimer              = game.getTurnTimerSeconds();
        int p1Timeouts             = game.getPlayer1ConsecutiveTimeouts();

        if (moveResult == GameMove.MoveResult.INVALID) {
            return new GameTransition(moveResult, scoreAfter, turnAdvanced, nextTurnPlayerId,
                    nextStatus, winnerId, nextTimer, p1Timeouts);
        }

        if (moveResult == GameMove.MoveResult.CHECKOUT) {
            log.info("Solo checkout: player {} wins", playerId);
            return new GameTransition(
                    GameMove.MoveResult.CHECKOUT, answerResult.getNewTotal(), false, null,
                    Game.GameStatus.COMPLETED, playerId,
                    DEFAULT_TIMER, 0);
        }

        // ── VALID or BUST ─────────────────────────────────────────────────
        if (moveResult == GameMove.MoveResult.VALID) {
            scoreAfter = answerResult.getNewTotal();
            p1Timeouts = 0;
            nextTimer = DEFAULT_TIMER;
        }
        // For BUST: scoreAfter stays = currentScore (no score change)

        turnAdvanced = true;
        // Solo mode: same player always continues

        return new GameTransition(moveResult, scoreAfter, turnAdvanced, nextTurnPlayerId,
                nextStatus, winnerId, nextTimer, p1Timeouts);
    }

    /**
     * Process a timeout event and compute the resulting {@link GameTransition}.
     *
     * @param game     current game entity
     * @param match    current match entity
     * @param playerId the player who timed out
     * @return an immutable transition descriptor; never {@code null}
     */
    public GameTransition onTimeout(Game game, Match match, UUID playerId) {
        int currentScore = getPlayerScore(game, match, playerId);
        int p1Timeouts = game.getPlayer1ConsecutiveTimeouts() + 1;

        // Forfeit threshold reached — solo bust-out, no winner
        if (p1Timeouts >= FORFEIT_TIMEOUT_THRESHOLD) {
            log.warn("Player {} busted out after {} consecutive timeouts", playerId, p1Timeouts);
            return new GameTransition(
                    GameMove.MoveResult.TIMEOUT, currentScore, true, null,
                    Game.GameStatus.COMPLETED, null,
                    game.getTurnTimerSeconds(),
                    p1Timeouts
            );
        }

        // Reduce timer based on accumulated consecutive timeouts
        int nextTimer = game.getTurnTimerSeconds();
        if (p1Timeouts == 1)      nextTimer = REDUCED_TIMER_1;
        else if (p1Timeouts == 2) nextTimer = REDUCED_TIMER_2;

        return new GameTransition(
                GameMove.MoveResult.TIMEOUT, currentScore, true, playerId,
                game.getStatus(), game.getWinnerId(),
                nextTimer, p1Timeouts
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Map an {@link AnswerResult} to the move classification the engine uses
     * for state-machine routing.
     */
    private GameMove.MoveResult classifyMove(AnswerResult answerResult) {
        if (!answerResult.isValid()) return GameMove.MoveResult.INVALID;
        if (answerResult.isWin())   return GameMove.MoveResult.CHECKOUT;
        if (answerResult.isBust())  return GameMove.MoveResult.BUST;
        return GameMove.MoveResult.VALID;
    }

    private int getPlayerScore(Game game, Match match, UUID playerId) {
        if (playerId.equals(match.getPlayer1Id())) return game.getPlayer1Score();
        throw new IllegalArgumentException("Player " + playerId + " is not part of match " + match.getId());
    }
}
