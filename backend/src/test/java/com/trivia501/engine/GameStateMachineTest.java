package com.trivia501.engine;

import com.trivia501.model.Game;
import com.trivia501.model.GameMove;
import com.trivia501.model.Match;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("GameStateMachine Tests")
class GameStateMachineTest {

    private GameStateMachine stateMachine;

    private UUID matchId;
    private UUID gameId;
    private UUID questionId;
    private UUID player1Id;
    private Match match;
    private Game game;

    @BeforeEach
    void setUp() {
        stateMachine = new GameStateMachine();

        matchId    = UUID.randomUUID();
        gameId     = UUID.randomUUID();
        questionId = UUID.randomUUID();
        player1Id  = UUID.randomUUID();

        match = Match.builder()
                .id(matchId)
                .player1Id(player1Id)
                .type(Match.MatchType.CASUAL)
                .format(Match.MatchFormat.BEST_OF_1)
                .status(Match.MatchStatus.IN_PROGRESS)
                .player1GamesWon(0)
                .build();

        game = Game.builder()
                .id(gameId)
                .matchId(matchId)
                .gameNumber(1)
                .questionId(questionId)
                .status(Game.GameStatus.IN_PROGRESS)
                .currentTurnPlayerId(player1Id)
                .player1Score(501)
                .playerConsecutiveTimeouts(0)
                .turnCount(0)
                .turnTimerSeconds(GameStateMachine.DEFAULT_TIMER)
                .build();
    }

    // ── Move: VALID ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("VALID move — deducts score, keeps same player, resets timeout counter")
    void validMove_shouldDeductScore() {
        AnswerResult answer = AnswerResult.valid("Erling Haaland", UUID.randomUUID(),
                36, true, false, 465, false, null, 0.95);

        GameTransition t = stateMachine.onMoveSubmitted(game, match, player1Id, answer);

        assertThat(t.moveResult()).isEqualTo(GameMove.MoveResult.VALID);
        assertThat(t.scoreAfter()).isEqualTo(465);
        assertThat(t.turnAdvanced()).isTrue();
        assertThat(t.nextTurnPlayerId()).isEqualTo(player1Id); // same player in solo
        assertThat(t.nextGameStatus()).isEqualTo(Game.GameStatus.IN_PROGRESS);
        assertThat(t.winnerId()).isNull();
        assertThat(t.nextTimerSeconds()).isEqualTo(GameStateMachine.DEFAULT_TIMER);
        assertThat(t.playerConsecutiveTimeouts()).isEqualTo(0);
    }

    @Test
    @DisplayName("VALID move with prior timeouts — resets consecutive timeout counter")
    void validMove_shouldResetConsecutiveTimeoutsAndTimer() {
        game.setPlayerConsecutiveTimeouts(2);
        game.setTurnTimerSeconds(GameStateMachine.REDUCED_TIMER_2);

        AnswerResult answer = AnswerResult.valid("Player", UUID.randomUUID(),
                25, true, false, 476, false, null, 0.9);

        GameTransition t = stateMachine.onMoveSubmitted(game, match, player1Id, answer);

        assertThat(t.playerConsecutiveTimeouts()).isEqualTo(0);
        assertThat(t.nextTimerSeconds()).isEqualTo(GameStateMachine.DEFAULT_TIMER);
    }

    // ── Move: INVALID ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("INVALID move — nothing changes, same player retries")
    void invalidMove_shouldChangeNothing() {
        AnswerResult answer = AnswerResult.invalid("Not found", 501);

        GameTransition t = stateMachine.onMoveSubmitted(game, match, player1Id, answer);

        assertThat(t.moveResult()).isEqualTo(GameMove.MoveResult.INVALID);
        assertThat(t.scoreAfter()).isEqualTo(501);
        assertThat(t.turnAdvanced()).isFalse();
        assertThat(t.nextTurnPlayerId()).isEqualTo(player1Id);
        assertThat(t.nextGameStatus()).isEqualTo(Game.GameStatus.IN_PROGRESS);
    }

    // ── Move: BUST ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("BUST move — no score change, same player retries")
    void bustMove_shouldKeepSamePlayer() {
        AnswerResult answer = AnswerResult.valid("Player with 179", UUID.randomUUID(),
                179, false, true, 501, false, "Invalid darts score", 0.9);

        GameTransition t = stateMachine.onMoveSubmitted(game, match, player1Id, answer);

        assertThat(t.moveResult()).isEqualTo(GameMove.MoveResult.BUST);
        assertThat(t.scoreAfter()).isEqualTo(501);
        assertThat(t.turnAdvanced()).isTrue();
        assertThat(t.nextTurnPlayerId()).isEqualTo(player1Id); // same player in solo
    }

    // ── Move: CHECKOUT ────────────────────────────────────────────────────────

    @Test
    @DisplayName("CHECKOUT — immediate win")
    void checkout_immediateWin() {
        game.setPlayer1Score(35);
        AnswerResult answer = AnswerResult.valid("Player", UUID.randomUUID(),
                35, true, false, 0, true, "Win!", 0.95);

        GameTransition t = stateMachine.onMoveSubmitted(game, match, player1Id, answer);

        assertThat(t.moveResult()).isEqualTo(GameMove.MoveResult.CHECKOUT);
        assertThat(t.scoreAfter()).isEqualTo(0);
        assertThat(t.nextGameStatus()).isEqualTo(Game.GameStatus.COMPLETED);
        assertThat(t.winnerId()).isEqualTo(player1Id);
        assertThat(t.nextTurnPlayerId()).isNull();
    }

    // ── Timeout ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("First timeout — increments counter to 1, reduces timer to 30s")
    void firstTimeout_reducesTimerTo30s() {
        GameTransition t = stateMachine.onTimeout(game, match, player1Id);

        assertThat(t.moveResult()).isEqualTo(GameMove.MoveResult.TIMEOUT);
        assertThat(t.playerConsecutiveTimeouts()).isEqualTo(1);
        assertThat(t.nextTimerSeconds()).isEqualTo(GameStateMachine.REDUCED_TIMER_1);
        assertThat(t.nextTurnPlayerId()).isEqualTo(player1Id); // same player in solo
        assertThat(t.nextGameStatus()).isEqualTo(Game.GameStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Second timeout — increments counter to 2, reduces timer to 15s")
    void secondTimeout_reducesTimerTo15s() {
        game.setPlayerConsecutiveTimeouts(1);
        game.setTurnTimerSeconds(GameStateMachine.REDUCED_TIMER_1);

        GameTransition t = stateMachine.onTimeout(game, match, player1Id);

        assertThat(t.playerConsecutiveTimeouts()).isEqualTo(2);
        assertThat(t.nextTimerSeconds()).isEqualTo(GameStateMachine.REDUCED_TIMER_2);
    }

    @Test
    @DisplayName("Third timeout — bust-out, no winner (solo)")
    void thirdTimeout_bustOut_noWinner() {
        game.setPlayerConsecutiveTimeouts(2);

        GameTransition t = stateMachine.onTimeout(game, match, player1Id);

        assertThat(t.playerConsecutiveTimeouts()).isEqualTo(3);
        assertThat(t.nextGameStatus()).isEqualTo(Game.GameStatus.COMPLETED);
        assertThat(t.winnerId()).isNull(); // solo bust-out
        assertThat(t.nextTurnPlayerId()).isNull();
    }

    @Test
    @DisplayName("Timeout — keeps same player (solo, no opponent)")
    void timeout_keepsCurrentPlayer() {
        GameTransition t = stateMachine.onTimeout(game, match, player1Id);

        assertThat(t.nextTurnPlayerId()).isEqualTo(player1Id);
    }
}
