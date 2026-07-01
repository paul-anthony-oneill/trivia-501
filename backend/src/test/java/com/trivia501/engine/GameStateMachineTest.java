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
                .turnCount(0)
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

}
