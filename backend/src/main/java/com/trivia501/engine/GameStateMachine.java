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
 *   <li><b>INVALID</b>  — same player retries; nothing changes</li>
 *   <li><b>BUST</b>     — no score change; same player retries</li>
 *   <li><b>VALID</b>    — score deducted; same player continues</li>
 *   <li><b>CHECKOUT</b> — immediate win</li>
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

        if (moveResult == GameMove.MoveResult.INVALID) {
            return new GameTransition(moveResult, scoreAfter, turnAdvanced, nextTurnPlayerId,
                    nextStatus, winnerId);
        }

        if (moveResult == GameMove.MoveResult.CHECKOUT) {
            log.info("Solo checkout: player {} wins", playerId);
            return new GameTransition(
                    GameMove.MoveResult.CHECKOUT, answerResult.getNewTotal(), false, null,
                    Game.GameStatus.COMPLETED, playerId);
        }

        // ── VALID or BUST ─────────────────────────────────────────────────
        if (moveResult == GameMove.MoveResult.VALID) {
            scoreAfter = answerResult.getNewTotal();
        }
        // For BUST: scoreAfter stays = currentScore (no score change)

        turnAdvanced = true;
        // Solo mode: same player always continues

        return new GameTransition(moveResult, scoreAfter, turnAdvanced, nextTurnPlayerId,
                nextStatus, winnerId);
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
