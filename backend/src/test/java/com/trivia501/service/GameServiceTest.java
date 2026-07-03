package com.trivia501.service;

import com.trivia501.engine.AnswerEvaluator;
import com.trivia501.engine.AnswerResult;
import com.trivia501.engine.GameStateMachine;
import com.trivia501.model.Game;
import com.trivia501.model.GameMove;
import com.trivia501.model.Match;
import com.trivia501.repository.AnswerRepository;
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
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    private AnswerRepository answerRepository;

    @Mock
    private AnswerEvaluator answerEvaluator;

    @Mock
    private PlayerProfileService playerProfileService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ResultSignerClient resultSignerClient;

    @Spy
    private GameStateMachine gameStateMachine;

    private GameService gameService;

    private UUID matchId;
    private UUID gameId;
    private UUID questionId;
    private UUID player1Id;
    private Match match;
    private Game game;

    @BeforeEach
    void setUp() {
        gameService = new GameService(
            gameRepository, gameMoveRepository, matchRepository,
            answerRepository, answerEvaluator, gameStateMachine, playerProfileService,
            eventPublisher, resultSignerClient);

        matchId = UUID.randomUUID();
        gameId = UUID.randomUUID();
        questionId = UUID.randomUUID();
        player1Id = UUID.randomUUID();

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

        // Default: viable moves remain (bust-out detection off for happy-path tests).
        // Lenient — tests that don't call processPlayerMove won't trigger this stub.
        lenient().when(answerRepository.hasViableMove(any(), anyInt(), anyList())).thenReturn(true);
    }

    @Test
    @DisplayName("Should process valid answer - deduct score, same player continues")
    void shouldProcessValidAnswer() {
        String playerAnswer = "Erling Haaland";
        UUID answerId = UUID.randomUUID();

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(gameMoveRepository.findUsedAnswerIdsByGameId(gameId)).thenReturn(List.of());

        AnswerResult answerResult = AnswerResult.valid(
            "Erling Haaland", answerId, 36, true, false, 465, false, null, 0.95);
        when(answerEvaluator.evaluateAnswer(eq(questionId), eq(playerAnswer), isNull(), eq(501), anyList()))
            .thenReturn(answerResult);

        GameService.MoveRecord record = gameService.processPlayerMove(gameId, player1Id, playerAnswer, null);
        GameMove result = record.move();

        assertThat(result).isNotNull();
        assertThat(result.getResult()).isEqualTo(GameMove.MoveResult.VALID);
        assertThat(result.getScoreBefore()).isEqualTo(501);
        assertThat(result.getScoreAfter()).isEqualTo(465);
        assertThat(result.getScoreValue()).isEqualTo(36);

        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(gameCaptor.capture());
        Game savedGame = gameCaptor.getValue();

        assertThat(savedGame.getPlayer1Score()).isEqualTo(465);
        assertThat(savedGame.getCurrentTurnPlayerId()).isEqualTo(player1Id); // Same player in solo
        assertThat(savedGame.getTurnCount()).isEqualTo(1);

        verify(gameMoveRepository).save(any(GameMove.class));
    }

    @Test
    @DisplayName("Should process invalid answer - allow immediate retry without switching turn")
    void shouldProcessInvalidAnswer() {
        String playerAnswer = "Unknown Player";

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(gameMoveRepository.findUsedAnswerIdsByGameId(gameId)).thenReturn(List.of());

        AnswerResult answerResult = AnswerResult.invalid("Answer not found or already used", 501);
        when(answerEvaluator.evaluateAnswer(eq(questionId), eq(playerAnswer), isNull(), eq(501), anyList()))
            .thenReturn(answerResult);

        GameService.MoveRecord record = gameService.processPlayerMove(gameId, player1Id, playerAnswer, null);

        assertThat(record.move().getResult()).isEqualTo(GameMove.MoveResult.INVALID);
        assertThat(record.move().getScoreBefore()).isEqualTo(501);
        assertThat(record.move().getScoreAfter()).isEqualTo(501);

        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(gameCaptor.capture());
        Game savedGame = gameCaptor.getValue();

        assertThat(savedGame.getCurrentTurnPlayerId()).isEqualTo(player1Id);
        assertThat(savedGame.getPlayer1Score()).isEqualTo(501);
    }

    @Test
    @DisplayName("Should process bust answer - no score change, same player continues")
    void shouldProcessBustAnswer() {
        String playerAnswer = "Player with 179 goals";

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(gameMoveRepository.findUsedAnswerIdsByGameId(gameId)).thenReturn(List.of());

        UUID answerId = UUID.randomUUID();
        AnswerResult answerResult = AnswerResult.valid(
            "Player Name", answerId, 179, false, true, 501, false, "Invalid darts score", 0.90);
        when(answerEvaluator.evaluateAnswer(eq(questionId), eq(playerAnswer), isNull(), eq(501), anyList()))
            .thenReturn(answerResult);

        GameService.MoveRecord record = gameService.processPlayerMove(gameId, player1Id, playerAnswer, null);

        assertThat(record.move().getResult()).isEqualTo(GameMove.MoveResult.BUST);
        assertThat(record.move().getScoreBefore()).isEqualTo(501);
        assertThat(record.move().getScoreAfter()).isEqualTo(501);

        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(gameCaptor.capture());
        Game savedGame = gameCaptor.getValue();

        assertThat(savedGame.getCurrentTurnPlayerId()).isEqualTo(player1Id); // Same player in solo
        assertThat(savedGame.getPlayer1Score()).isEqualTo(501);
    }

    @Test
    @DisplayName("Should process checkout answer - immediate win")
    void shouldProcessCheckoutAnswer() {
        game.setPlayer1Score(35);
        String playerAnswer = "Player with 35";

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(gameMoveRepository.findUsedAnswerIdsByGameId(gameId)).thenReturn(List.of());

        UUID answerId = UUID.randomUUID();
        AnswerResult answerResult = AnswerResult.valid(
            "Player Name", answerId, 35, true, false, 0, true, "Win!", 0.95);
        when(answerEvaluator.evaluateAnswer(eq(questionId), eq(playerAnswer), isNull(), eq(35), anyList()))
            .thenReturn(answerResult);

        GameService.MoveRecord record = gameService.processPlayerMove(gameId, player1Id, playerAnswer, null);

        assertThat(record.move().getResult()).isEqualTo(GameMove.MoveResult.CHECKOUT);
        assertThat(record.move().getScoreAfter()).isEqualTo(0);

        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(gameCaptor.capture());
        Game savedGame = gameCaptor.getValue();

        assertThat(savedGame.getStatus()).isEqualTo(Game.GameStatus.COMPLETED); // Immediate win
        assertThat(savedGame.getWinnerId()).isEqualTo(player1Id);
        assertThat(savedGame.getPlayer1Score()).isEqualTo(0);

        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Should create new game within match")
    void shouldCreateNewGame() {
        UUID categoryId = UUID.randomUUID();
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(gameRepository.save(any(Game.class))).thenAnswer(i -> i.getArgument(0));

        Game result = gameService.createGame(matchId, questionId, 1, 501);

        assertThat(result).isNotNull();
        assertThat(result.getMatchId()).isEqualTo(matchId);
        assertThat(result.getQuestionId()).isEqualTo(questionId);
        assertThat(result.getGameNumber()).isEqualTo(1);
        assertThat(result.getPlayer1Score()).isEqualTo(501);
        assertThat(result.getStatus()).isEqualTo(Game.GameStatus.IN_PROGRESS);
        assertThat(result.getCurrentTurnPlayerId()).isEqualTo(player1Id);

        verify(gameRepository).save(any(Game.class));
    }

    @Test
    @DisplayName("Should get used answer IDs from previous moves")
    void shouldGetUsedAnswerIds() {
        UUID answer1Id = UUID.randomUUID();
        UUID answer2Id = UUID.randomUUID();

        when(gameMoveRepository.findUsedAnswerIdsByGameId(gameId))
            .thenReturn(List.of(answer1Id, answer2Id));

        List<UUID> result = gameService.getUsedAnswerIds(gameId);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(answer1Id, answer2Id);
        verify(gameMoveRepository).findUsedAnswerIdsByGameId(gameId);
    }

    @Test
    @DisplayName("Should get game by ID")
    void shouldGetGameById() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));

        Optional<Game> result = gameService.getGameById(gameId);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(game);
    }

    @Test
    @DisplayName("Should throw exception when wrong player submits move")
    void shouldThrowExceptionWhenWrongPlayerSubmits() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        UUID strangerId = UUID.randomUUID();
        assertThatThrownBy(() ->
            gameService.processPlayerMove(gameId, strangerId, "Some answer", null)
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Not player's turn");
    }

    @Test
    @DisplayName("Should throw exception when game is not in progress")
    void shouldThrowExceptionWhenGameNotInProgress() {
        game.setStatus(Game.GameStatus.COMPLETED);
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

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
    @DisplayName("Should throw when abandoning non-existent game")
    void shouldThrowWhenAbandoningNonExistentGame() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            gameService.abandonGame(gameId, player1Id)
        )
            .isInstanceOf(IllegalArgumentException.class);
    }
}
