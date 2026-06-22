package com.trivia501.service;

import com.trivia501.event.GameCompletedEvent;
import com.trivia501.model.Game;
import com.trivia501.model.Match;
import com.trivia501.model.Question;
import com.trivia501.repository.GameRepository;
import com.trivia501.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchService Tests")
class MatchServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameService gameService;

    @Mock
    private QuestionService questionService;

    @InjectMocks
    private MatchService matchService;

    private UUID player1Id;
    private UUID categoryId;
    private UUID matchId;
    private UUID questionId;
    private Match match;
    private Question question;

    @BeforeEach
    void setUp() {
        player1Id = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        matchId = UUID.randomUUID();
        questionId = UUID.randomUUID();

        match = Match.builder()
            .id(matchId)
            .player1Id(player1Id)
            .type(Match.MatchType.CASUAL)
            .format(Match.MatchFormat.BEST_OF_1)
            .status(Match.MatchStatus.IN_PROGRESS)
            .categoryId(categoryId)
            .player1GamesWon(0)
            .build();

        question = Question.builder()
            .id(questionId)
            .categoryId(categoryId)
            .questionText("Test Question")
            .metricKey("goals")
            .status(Question.STATUS_ACTIVE)
            .build();
    }

    @Test
    @DisplayName("Should create new match")
    void shouldCreateNewMatch() {
        when(matchRepository.save(any(Match.class))).thenAnswer(i -> i.getArgument(0));

        Match result = matchService.createMatch(
            player1Id,
            categoryId,
            Match.MatchType.CASUAL,
            Match.MatchFormat.BEST_OF_1,
            2
        );

        assertThat(result).isNotNull();
        assertThat(result.getPlayer1Id()).isEqualTo(player1Id);
        assertThat(result.getCategoryId()).isEqualTo(categoryId);
        assertThat(result.getType()).isEqualTo(Match.MatchType.CASUAL);
        assertThat(result.getFormat()).isEqualTo(Match.MatchFormat.BEST_OF_1);
        assertThat(result.getStatus()).isEqualTo(Match.MatchStatus.IN_PROGRESS);
        assertThat(result.getPlayer1GamesWon()).isEqualTo(0);

        verify(matchRepository).save(any(Match.class));
    }

    @Test
    @DisplayName("Should start first game in match")
    void shouldStartFirstGame() {
        when(questionService.selectRandomQuestion(eq(categoryId), eq(2), eq(10)))
            .thenReturn(Optional.of(question));
        when(gameRepository.countByMatchIdAndStatus(matchId, Game.GameStatus.COMPLETED))
            .thenReturn(0L);

        Game createdGame = Game.builder()
            .id(UUID.randomUUID())
            .matchId(matchId)
            .gameNumber(1)
            .questionId(questionId)
            .status(Game.GameStatus.IN_PROGRESS)
            .build();

        when(gameService.createGame(matchId, questionId, 1, 501)).thenReturn(createdGame);

        MatchService.GameStartRecord startRecord = matchService.startNextGame(match);

        assertThat(startRecord.game()).isNotNull();
        assertThat(startRecord.game().getGameNumber()).isEqualTo(1);
        assertThat(startRecord.game().getQuestionId()).isEqualTo(questionId);
        assertThat(startRecord.question()).isEqualTo(question);

        verify(questionService).selectRandomQuestion(eq(categoryId), eq(2), eq(10));
        verify(gameService).createGame(matchId, questionId, 1, 501);
    }

    @Test
    @DisplayName("Should start next game after previous completes")
    void shouldStartNextGameAfterPreviousCompletes() {
        when(questionService.selectRandomQuestion(eq(categoryId), eq(2), eq(10)))
            .thenReturn(Optional.of(question));
        when(gameRepository.countByMatchIdAndStatus(matchId, Game.GameStatus.COMPLETED))
            .thenReturn(1L);

        Game createdGame = Game.builder()
            .id(UUID.randomUUID())
            .matchId(matchId)
            .gameNumber(2)
            .questionId(questionId)
            .status(Game.GameStatus.IN_PROGRESS)
            .build();

        when(gameService.createGame(matchId, questionId, 2, 501)).thenReturn(createdGame);

        MatchService.GameStartRecord startRecord = matchService.startNextGame(match);

        assertThat(startRecord.game().getGameNumber()).isEqualTo(2);
        verify(gameService).createGame(matchId, questionId, 2, 501);
    }

    @Test
    @DisplayName("Should handle game completion and update match wins")
    void shouldHandleGameCompletionAndUpdateWins() {
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        Game completedGame = Game.builder()
            .id(UUID.randomUUID())
            .matchId(matchId)
            .gameNumber(1)
            .status(Game.GameStatus.COMPLETED)
            .winnerId(player1Id)
            .build();

        matchService.handleGameCompletion(completedGame);

        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        Match savedMatch = matchCaptor.getValue();

        assertThat(savedMatch.getPlayer1GamesWon()).isEqualTo(1);
        assertThat(savedMatch.getStatus()).isEqualTo(Match.MatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should check if match is complete")
    void shouldCheckIfMatchIsComplete() {
        match.setPlayer1GamesWon(1);

        boolean result = matchService.isMatchComplete(match);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should check if match is NOT complete")
    void shouldCheckIfMatchIsNotComplete() {
        match.setPlayer1GamesWon(0);

        boolean result = matchService.isMatchComplete(match);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should get active matches for a player")
    void shouldGetActiveMatchesForPlayer() {
        Match match1 = Match.builder()
            .id(UUID.randomUUID())
            .player1Id(player1Id)
            .status(Match.MatchStatus.IN_PROGRESS)
            .build();

        Match match2 = Match.builder()
            .id(UUID.randomUUID())
            .player1Id(player1Id)
            .status(Match.MatchStatus.IN_PROGRESS)
            .build();

        when(matchRepository.findActiveMatchesByPlayerId(player1Id))
            .thenReturn(List.of(match1, match2));

        List<Match> result = matchService.getActiveMatchesForPlayer(player1Id);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(match1, match2);
    }

    @Test
    @DisplayName("Should get match by ID")
    void shouldGetMatchById() {
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        Optional<Match> result = matchService.getMatchById(matchId);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(match);
    }

    @Test
    @DisplayName("Should throw exception when starting game on completed match")
    void shouldThrowExceptionWhenStartingGameOnCompletedMatch() {
        match.setStatus(Match.MatchStatus.COMPLETED);

        assertThatThrownBy(() ->
            matchService.startNextGame(match)
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Match is not in progress");
    }

    @Test
    @DisplayName("Should throw exception when no question available")
    void shouldThrowExceptionWhenNoQuestionAvailable() {
        when(questionService.selectRandomQuestion(eq(categoryId), eq(2), eq(10)))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            matchService.startNextGame(match)
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No question available");
    }

    @Test
    @DisplayName("Should get match statistics for a player")
    void shouldGetMatchStatisticsForPlayer() {
        when(matchRepository.countWinsByPlayerId(player1Id)).thenReturn(15L);
        when(matchRepository.countLossesByPlayerId(player1Id)).thenReturn(8L);

        MatchService.MatchStats result = matchService.getMatchStats(player1Id);

        assertThat(result).isNotNull();
        assertThat(result.wins()).isEqualTo(15);
        assertThat(result.losses()).isEqualTo(8);
        assertThat(result.total()).isEqualTo(23);
    }

    @Test
    @DisplayName("Should get all games for a match")
    void shouldGetAllGamesForMatch() {
        Game game1 = Game.builder()
            .id(UUID.randomUUID())
            .matchId(matchId)
            .gameNumber(1)
            .build();

        Game game2 = Game.builder()
            .id(UUID.randomUUID())
            .matchId(matchId)
            .gameNumber(2)
            .build();

        when(gameRepository.findByMatchIdOrderByGameNumberAsc(matchId))
            .thenReturn(List.of(game1, game2));

        List<Game> result = matchService.getGamesForMatch(matchId);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(game1, game2);
        verify(gameRepository).findByMatchIdOrderByGameNumberAsc(matchId);
    }

    @Test
    @DisplayName("Should get current active game for a match")
    void shouldGetCurrentActiveGame() {
        Game activeGame = Game.builder()
            .id(UUID.randomUUID())
            .matchId(matchId)
            .gameNumber(2)
            .status(Game.GameStatus.IN_PROGRESS)
            .build();

        when(gameRepository.findByMatchIdAndStatus(matchId, Game.GameStatus.IN_PROGRESS))
            .thenReturn(Optional.of(activeGame));

        Optional<Game> result = matchService.getCurrentGame(matchId);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(activeGame);
    }

    @Test
    @DisplayName("Should handle best-of-1 match completion")
    void shouldHandleBestOf1MatchCompletion() {
        match.setFormat(Match.MatchFormat.BEST_OF_1);
        match.setPlayer1GamesWon(0);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        Game completedGame = Game.builder()
            .id(UUID.randomUUID())
            .matchId(matchId)
            .gameNumber(1)
            .status(Game.GameStatus.COMPLETED)
            .winnerId(player1Id)
            .build();

        matchService.handleGameCompletion(completedGame);

        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        Match savedMatch = matchCaptor.getValue();

        assertThat(savedMatch.getPlayer1GamesWon()).isEqualTo(1);
        assertThat(savedMatch.getStatus()).isEqualTo(Match.MatchStatus.COMPLETED);
        assertThat(savedMatch.getWinnerId()).isEqualTo(player1Id);
    }

    @Test
    @DisplayName("Should handle onGameCompleted event")
    void shouldHandleOnGameCompletedEvent() {
        when(gameService.getGameById(any())).thenReturn(Optional.empty());

        GameCompletedEvent event = new GameCompletedEvent(UUID.randomUUID(), matchId, null, true);
        assertThatThrownBy(() -> matchService.onGameCompleted(event))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Game not found");
    }
}
