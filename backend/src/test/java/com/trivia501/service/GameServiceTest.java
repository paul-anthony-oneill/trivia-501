package com.trivia501.service;

import com.trivia501.engine.AnswerEvaluator;
import com.trivia501.engine.AnswerResult;
import com.trivia501.engine.GameStateMachine;
import com.trivia501.model.Game;
import com.trivia501.model.GameMove;
import com.trivia501.model.Match;
import com.trivia501.repository.GameMoveRepository;
import com.trivia501.repository.GameRepository;
import com.trivia501.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TDD Tests for GameService.
 *
 * Test scenarios:
 * 1. Process valid answer - score deducted, turn switches
 * 2. Process invalid answer - no score change, can retry immediately
 * 3. Process bust answer - no score change, turn switches
 * 4. Process checkout answer - player wins
 * 5. Track consecutive timeouts per player
 * 6. Reduce timer on consecutive timeouts (45s → 30s → 15s)
 * 7. Reset consecutive timeouts on successful move
 * 8. Close finish rule - Player 2 gets final turn if Player 1 checks out
 * 9. Create new game within match
 * 10. Get used answer IDs from previous moves
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GameService Tests")
class GameServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameMoveRepository gameMoveRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private AnswerEvaluator answerEvaluator;

    @Mock
    private PlayerProfileService playerProfileService;

    @Mock
    private MatchService matchService;

    @Mock
    private ResultSignerClient resultSignerClient;

    /**
     * Use a real GameStateMachine (no DB dependencies) so all transition-logic
     * assertions continue to pass without per-test stubbing.
     */
    @Spy
    private GameStateMachine gameStateMachine;

    private GameService gameService;

    private UUID matchId;
    private UUID gameId;
    private UUID questionId;
    private UUID player1Id;
    private UUID player2Id;
    private Match match;
    private Game game;

    @BeforeEach
    void setUp() {
        // Manual construction — @InjectMocks can't handle the @Lazy MatchService parameter
        gameService = new GameService(
            gameRepository, gameMoveRepository, matchRepository,
            answerEvaluator, gameStateMachine, playerProfileService, matchService,
            resultSignerClient);

        matchId = UUID.randomUUID();
        gameId = UUID.randomUUID();
        questionId = UUID.randomUUID();
        player1Id = UUID.randomUUID();
        player2Id = UUID.randomUUID();

        match = Match.builder()
            .id(matchId)
            .player1Id(player1Id)
            .player2Id(player2Id)
            .type(Match.MatchType.CASUAL)
            .format(Match.MatchFormat.BEST_OF_3)
            .status(Match.MatchStatus.IN_PROGRESS)
            .player1GamesWon(0)
            .player2GamesWon(0)
            .build();

        game = Game.builder()
            .id(gameId)
            .matchId(matchId)
            .gameNumber(1)
            .questionId(questionId)
            .status(Game.GameStatus.IN_PROGRESS)
            .currentTurnPlayerId(player1Id)
            .player1Score(501)
            .player2Score(501)
            .player1ConsecutiveTimeouts(0)
            .player2ConsecutiveTimeouts(0)
            .turnCount(0)
            .turnTimerSeconds(45)
            .build();
    }

    @Test
    @DisplayName("Should process valid answer - deduct score and switch turn")
    void shouldProcessValidAnswer() {
        // Given
        String playerAnswer = "Erling Haaland";
        UUID answerId = UUID.randomUUID();

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(gameMoveRepository.findUsedAnswerIdsByGameId(gameId)).thenReturn(List.of());

        // Mock valid answer with score 36
        AnswerResult answerResult = AnswerResult.valid(
            "Erling Haaland",
            answerId,
            36,
            true, // valid darts score
            false, // not bust
            465, // new score (501 - 36)
            false, // not win
            null,
            0.95
        );
        when(answerEvaluator.evaluateAnswer(eq(questionId), eq(playerAnswer), isNull(), eq(501), anyList()))
            .thenReturn(answerResult);

        // When
        GameService.MoveRecord record = gameService.processPlayerMove(gameId, player1Id, playerAnswer, null);
        GameMove result = record.move();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getResult()).isEqualTo(GameMove.MoveResult.VALID);
        assertThat(result.getScoreBefore()).isEqualTo(501);
        assertThat(result.getScoreAfter()).isEqualTo(465);
        assertThat(result.getScoreValue()).isEqualTo(36);

        // Verify game state updated
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(gameCaptor.capture());
        Game savedGame = gameCaptor.getValue();

        assertThat(savedGame.getPlayer1Score()).isEqualTo(465);
        assertThat(savedGame.getCurrentTurnPlayerId()).isEqualTo(player2Id); // Turn switched
        assertThat(savedGame.getTurnCount()).isEqualTo(1);
        assertThat(savedGame.getPlayer1ConsecutiveTimeouts()).isEqualTo(0); // Reset on success

        // Verify move saved
        verify(gameMoveRepository).save(any(GameMove.class));
    }

    @Test
    @DisplayName("Should process invalid answer - allow immediate retry without switching turn")
    void shouldProcessInvalidAnswer() {
        // Given
        String playerAnswer = "Unknown Player";

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(gameMoveRepository.findUsedAnswerIdsByGameId(gameId)).thenReturn(List.of());

        AnswerResult answerResult = AnswerResult.invalid("Answer not found or already used", 501);
        when(answerEvaluator.evaluateAnswer(eq(questionId), eq(playerAnswer), isNull(), eq(501), anyList()))
            .thenReturn(answerResult);

        // When
        GameService.MoveRecord record = gameService.processPlayerMove(gameId, player1Id, playerAnswer, null);

        // Then
        assertThat(record.move().getResult()).isEqualTo(GameMove.MoveResult.INVALID);
        assertThat(record.move().getScoreBefore()).isEqualTo(501);
        assertThat(record.move().getScoreAfter()).isEqualTo(501); // No change

        // Verify turn did NOT switch
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(gameCaptor.capture());
        Game savedGame = gameCaptor.getValue();

        assertThat(savedGame.getCurrentTurnPlayerId()).isEqualTo(player1Id); // Same player
        assertThat(savedGame.getPlayer1Score()).isEqualTo(501); // No change
    }

    @Test
    @DisplayName("Should process bust answer - no score change but turn switches")
    void shouldProcessBustAnswer() {
        // Given
        String playerAnswer = "Player with 179 goals"; // 179 is invalid darts score

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(gameMoveRepository.findUsedAnswerIdsByGameId(gameId)).thenReturn(List.of());

        UUID answerId = UUID.randomUUID();
        AnswerResult answerResult = AnswerResult.valid(
            "Player Name",
            answerId,
            179,
            false, // NOT valid darts score
            true, // BUST
            501, // score unchanged
            false,
            "Invalid darts score",
            0.90
        );
        when(answerEvaluator.evaluateAnswer(eq(questionId), eq(playerAnswer), isNull(), eq(501), anyList()))
            .thenReturn(answerResult);

        // When
        GameService.MoveRecord record = gameService.processPlayerMove(gameId, player1Id, playerAnswer, null);

        // Then
        assertThat(record.move().getResult()).isEqualTo(GameMove.MoveResult.BUST);
        assertThat(record.move().getScoreBefore()).isEqualTo(501);
        assertThat(record.move().getScoreAfter()).isEqualTo(501); // No change

        // Verify turn SWITCHED (bust wastes turn)
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(gameCaptor.capture());
        Game savedGame = gameCaptor.getValue();

        assertThat(savedGame.getCurrentTurnPlayerId()).isEqualTo(player2Id); // Turn switched
        assertThat(savedGame.getPlayer1Score()).isEqualTo(501); // No score change
    }

    @Test
    @DisplayName("Should process checkout answer - player wins (close finish rule applies)")
    void shouldProcessCheckoutAnswer() {
        // Given - Player 1 at score 35
        game.setPlayer1Score(35);
        String playerAnswer = "Player with 35";

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(gameMoveRepository.findUsedAnswerIdsByGameId(gameId)).thenReturn(List.of());

        UUID answerId = UUID.randomUUID();
        AnswerResult answerResult = AnswerResult.valid(
            "Player Name",
            answerId,
            35,
            true,
            false,
            0, // Checkout! (35 - 35 = 0)
            true, // WIN
            "Win!",
            0.95
        );
        when(answerEvaluator.evaluateAnswer(eq(questionId), eq(playerAnswer), isNull(), eq(35), anyList()))
            .thenReturn(answerResult);

        // When
        GameService.MoveRecord record = gameService.processPlayerMove(gameId, player1Id, playerAnswer, null);

        // Then
        assertThat(record.move().getResult()).isEqualTo(GameMove.MoveResult.CHECKOUT);
        assertThat(record.move().getScoreAfter()).isEqualTo(0);

        // Verify game state updated with winner (but not completed yet - close finish rule)
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(gameCaptor.capture());
        Game savedGame = gameCaptor.getValue();

        // Close finish rule: P1 checked out, so P2 gets final turn
        assertThat(savedGame.getStatus()).isEqualTo(Game.GameStatus.IN_PROGRESS); // Not completed yet!
        assertThat(savedGame.getWinnerId()).isEqualTo(player1Id); // Tentative winner
        assertThat(savedGame.getCurrentTurnPlayerId()).isEqualTo(player2Id); // P2's final turn
        assertThat(savedGame.getPlayer1Score()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should track consecutive timeouts for player")
    void shouldTrackConsecutiveTimeouts() {
        // Given - Player 1 has 1 consecutive timeout already
        game.setPlayer1ConsecutiveTimeouts(1);

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        // When
        gameService.handleTimeout(gameId, player1Id);

        // Then
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(gameCaptor.capture());
        Game savedGame = gameCaptor.getValue();

        assertThat(savedGame.getPlayer1ConsecutiveTimeouts()).isEqualTo(2);
        assertThat(savedGame.getCurrentTurnPlayerId()).isEqualTo(player2Id); // Turn switches

        // Verify timeout move saved
        ArgumentCaptor<GameMove> moveCaptor = ArgumentCaptor.forClass(GameMove.class);
        verify(gameMoveRepository).save(moveCaptor.capture());
        GameMove move = moveCaptor.getValue();

        assertThat(move.getResult()).isEqualTo(GameMove.MoveResult.TIMEOUT);
        assertThat(move.getIsTimeout()).isTrue();
    }

    @Test
    @DisplayName("Should reduce timer to 30s after 1st consecutive timeout")
    void shouldReduceTimerTo30sAfterFirstTimeout() {
        // Given - Player 1 has no prior timeouts
        game.setPlayer1ConsecutiveTimeouts(0);
        game.setTurnTimerSeconds(45);

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        // When - First timeout
        gameService.handleTimeout(gameId, player1Id);

        // Then
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(gameCaptor.capture());
        Game savedGame = gameCaptor.getValue();

        assertThat(savedGame.getPlayer1ConsecutiveTimeouts()).isEqualTo(1);
        assertThat(savedGame.getTurnTimerSeconds()).isEqualTo(30); // 45s → 30s
    }

    @Test
    @DisplayName("Should reduce timer to 15s after 2nd consecutive timeout")
    void shouldReduceTimerTo15sAfterSecondTimeout() {
        // Given - Player 1 has 1 consecutive timeout
        game.setPlayer1ConsecutiveTimeouts(1);
        game.setTurnTimerSeconds(30);

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        // When - Second timeout
        gameService.handleTimeout(gameId, player1Id);

        // Then
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(gameCaptor.capture());
        Game savedGame = gameCaptor.getValue();

        assertThat(savedGame.getPlayer1ConsecutiveTimeouts()).isEqualTo(2);
        assertThat(savedGame.getTurnTimerSeconds()).isEqualTo(15); // 30s → 15s
    }

    @Test
    @DisplayName("Should reset consecutive timeouts on successful move")
    void shouldResetConsecutiveTimeoutsOnSuccess() {
        // Given - Player 1 has consecutive timeouts
        game.setPlayer1ConsecutiveTimeouts(2);
        game.setTurnTimerSeconds(30);

        String playerAnswer = "Valid Player";
        UUID answerId = UUID.randomUUID();

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(gameMoveRepository.findUsedAnswerIdsByGameId(gameId)).thenReturn(List.of());

        AnswerResult answerResult = AnswerResult.valid(
            "Valid Player",
            answerId,
            25,
            true,
            false,
            476,
            false,
            null,
            0.95
        );
        when(answerEvaluator.evaluateAnswer(any(), any(), isNull(), anyInt(), anyList()))
            .thenReturn(answerResult);

        // When
        gameService.processPlayerMove(gameId, player1Id, playerAnswer, null);

        // Then
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(gameCaptor.capture());
        Game savedGame = gameCaptor.getValue();

        assertThat(savedGame.getPlayer1ConsecutiveTimeouts()).isEqualTo(0); // Reset!
        assertThat(savedGame.getTurnTimerSeconds()).isEqualTo(45); // Reset to default
    }

    @Test
    @DisplayName("Should forfeit game after 3 consecutive timeouts")
    void shouldForfeitAfterThreeConsecutiveTimeouts() {
        // Given - Player 1 has 2 consecutive timeouts already
        game.setPlayer1ConsecutiveTimeouts(2);

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        // When - Third consecutive timeout
        gameService.handleTimeout(gameId, player1Id);

        // Then
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(gameCaptor.capture());
        Game savedGame = gameCaptor.getValue();

        assertThat(savedGame.getPlayer1ConsecutiveTimeouts()).isEqualTo(3);
        assertThat(savedGame.getStatus()).isEqualTo(Game.GameStatus.COMPLETED);
        assertThat(savedGame.getWinnerId()).isEqualTo(player2Id); // Opponent wins by forfeit
    }

    @Test
    @DisplayName("Should apply close finish rule - Player 2 can beat Player 1")
    void shouldApplyCloseFinishRule() {
        // Given - Player 1 checked out at -3 (close finish rule in effect)
        game.setPlayer1Score(-3);
        game.setPlayer2Score(50);
        game.setStatus(Game.GameStatus.IN_PROGRESS); // Still in progress!
        game.setWinnerId(player1Id); // P1 is tentative winner
        game.setCurrentTurnPlayerId(player2Id); // Close finish rule - P2 gets final turn

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(gameMoveRepository.findUsedAnswerIdsByGameId(gameId)).thenReturn(List.of());

        // Player 2 submits answer that gets them to -2 (closer to 0 than P1's -3)
        String playerAnswer = "Player with 52 goals";
        UUID answerId = UUID.randomUUID();

        AnswerResult answerResult = AnswerResult.valid(
            "Player Name",
            answerId,
            52,
            true,
            false,
            -2, // Closer to 0 than Player 1's -3!
            true,
            "Win!",
            0.95
        );
        when(answerEvaluator.evaluateAnswer(eq(questionId), eq(playerAnswer), isNull(), eq(50), anyList()))
            .thenReturn(answerResult);

        // When
        GameService.MoveRecord record = gameService.processPlayerMove(gameId, player2Id, playerAnswer, null);

        // Then
        assertThat(record.move().getResult()).isEqualTo(GameMove.MoveResult.CHECKOUT);

        // Verify Player 2 is now the winner (closer to 0: -2 vs -3) and game is completed
        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(gameCaptor.capture());
        Game savedGame = gameCaptor.getValue();

        assertThat(savedGame.getStatus()).isEqualTo(Game.GameStatus.COMPLETED);
        assertThat(savedGame.getWinnerId()).isEqualTo(player2Id);
        assertThat(savedGame.getPlayer2Score()).isEqualTo(-2);
    }

    @Test
    @DisplayName("Should create new game within match")
    void shouldCreateNewGame() {
        // Given
        UUID categoryId = UUID.randomUUID();
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(gameRepository.save(any(Game.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Game result = gameService.createGame(matchId, questionId, 1, 501);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMatchId()).isEqualTo(matchId);
        assertThat(result.getQuestionId()).isEqualTo(questionId);
        assertThat(result.getGameNumber()).isEqualTo(1);
        assertThat(result.getPlayer1Score()).isEqualTo(501);
        assertThat(result.getPlayer2Score()).isEqualTo(501);
        assertThat(result.getStatus()).isEqualTo(Game.GameStatus.IN_PROGRESS);
        assertThat(result.getCurrentTurnPlayerId()).isEqualTo(player1Id); // Player 1 starts

        verify(gameRepository).save(any(Game.class));
    }

    @Test
    @DisplayName("Should get used answer IDs from previous moves")
    void shouldGetUsedAnswerIds() {
        // Given
        UUID answer1Id = UUID.randomUUID();
        UUID answer2Id = UUID.randomUUID();

        when(gameMoveRepository.findUsedAnswerIdsByGameId(gameId))
            .thenReturn(List.of(answer1Id, answer2Id));

        // When
        List<UUID> result = gameService.getUsedAnswerIds(gameId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(answer1Id, answer2Id);
        verify(gameMoveRepository).findUsedAnswerIdsByGameId(gameId);
    }

    @Test
    @DisplayName("Should get game by ID")
    void shouldGetGameById() {
        // Given
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));

        // When
        Optional<Game> result = gameService.getGameById(gameId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(game);
    }

    @Test
    @DisplayName("Should throw exception when wrong player submits move")
    void shouldThrowExceptionWhenWrongPlayerSubmits() {
        // Given - Current turn is Player 1
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        // When/Then - Player 2 tries to submit
        assertThatThrownBy(() ->
            gameService.processPlayerMove(gameId, player2Id, "Some answer", null)
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Not player's turn");
    }

    @Test
    @DisplayName("Should throw exception when game is not in progress")
    void shouldThrowExceptionWhenGameNotInProgress() {
        // Given
        game.setStatus(Game.GameStatus.COMPLETED);
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        // When/Then
        assertThatThrownBy(() ->
            gameService.processPlayerMove(gameId, player1Id, "Some answer", null)
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Game is not in progress");
    }

    // ── Abandonment tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Should abandon in-progress game and match")
    void shouldAbandonInProgressGameAndMatch() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        gameService.abandonGame(gameId, player1Id);

        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(gameCaptor.capture());
        Game savedGame = gameCaptor.getValue();
        assertThat(savedGame.getStatus()).isEqualTo(Game.GameStatus.ABANDONED);
        assertThat(savedGame.getCompletedAt()).isNotNull();

        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        Match savedMatch = matchCaptor.getValue();
        assertThat(savedMatch.getStatus()).isEqualTo(Match.MatchStatus.ABANDONED);
        assertThat(savedMatch.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should be idempotent when game and match are already completed")
    void shouldBeIdempotentWhenAlreadyCompleted() {
        game.setStatus(Game.GameStatus.COMPLETED);
        match.setStatus(Match.MatchStatus.COMPLETED);

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        gameService.abandonGame(gameId, player1Id);

        verify(gameRepository, never()).save(any(Game.class));
        verify(matchRepository, never()).save(any(Match.class));
    }

    @Test
    @DisplayName("Should be idempotent when game is already abandoned")
    void shouldBeIdempotentWhenAlreadyAbandoned() {
        game.setStatus(Game.GameStatus.ABANDONED);
        match.setStatus(Match.MatchStatus.ABANDONED);

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        gameService.abandonGame(gameId, player1Id);

        verify(gameRepository, never()).save(any(Game.class));
        verify(matchRepository, never()).save(any(Match.class));
    }

    @Test
    @DisplayName("Should reject abandonment by player not in the match")
    void shouldRejectAbandonmentByNonParticipant() {
        UUID strangerId = UUID.randomUUID();

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        assertThatThrownBy(() ->
            gameService.abandonGame(gameId, strangerId)
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not part of match");
    }

    @Test
    @DisplayName("Should allow abandonment by player 2")
    void shouldAllowAbandonmentByPlayer2() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        gameService.abandonGame(gameId, player2Id);

        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(gameCaptor.capture());
        assertThat(gameCaptor.getValue().getStatus()).isEqualTo(Game.GameStatus.ABANDONED);
    }

    @Test
    @DisplayName("Should throw when abandoning non-existent game")
    void shouldThrowWhenAbandoningNonExistentGame() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            gameService.abandonGame(gameId, player1Id)
        )
            .isInstanceOf(IllegalArgumentException.class);
    }
}
