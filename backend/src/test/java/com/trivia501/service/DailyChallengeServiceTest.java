package com.trivia501.service;

import com.trivia501.engine.ChallengeScorePicker;
import com.trivia501.model.Category;
import com.trivia501.model.DailyChallenge;
import com.trivia501.model.Game;
import com.trivia501.model.Match;
import com.trivia501.model.Question;
import com.trivia501.repository.CategoryRepository;
import com.trivia501.repository.DailyChallengeRepository;
import com.trivia501.repository.GameRepository;
import com.trivia501.repository.MatchRepository;
import com.trivia501.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyChallengeServiceTest {

    @Mock private DailyChallengeRepository challengeRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private MatchService matchService;
    @Mock private GameService gameService;
    @Mock private GameRepository gameRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private ChallengeScorePicker scorePicker;

    private final java.time.Clock clock = java.time.Clock.systemUTC();

    private DailyChallengeService service;

    private final UUID categoryId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();
    private final Category category = Category.builder()
            .id(categoryId).slug("football").name("Football").build();
    private final Question question = Question.builder()
            .id(questionId).categoryId(categoryId).questionText("Test question")
            .status(Question.STATUS_ACTIVE).difficultyScore(3.0).totalScorePool(600).build();

    @BeforeEach
    void setUp() {
        service = new DailyChallengeService(
                challengeRepository, questionRepository, categoryRepository,
                matchService, gameService, gameRepository,
                matchRepository, scorePicker, clock);
    }

    @Test
    void shouldReturnExistingChallengeForToday() {
        DailyChallenge existing = DailyChallenge.builder()
                .challengeDate(LocalDate.now()).categoryId(categoryId).questionId(questionId)
                .startingScore(301).status("active").build();

        when(challengeRepository.findByChallengeDateAndCategoryId(LocalDate.now(), categoryId))
                .thenReturn(Optional.of(existing));

        DailyChallenge result = service.getTodaysChallenge(categoryId);

        assertThat(result).isEqualTo(existing);
        assertThat(result.getStartingScore()).isEqualTo(301);
    }

    @Test
    void shouldCreateChallengeLazilyWhenNoneExists() {
        when(challengeRepository.findByChallengeDateAndCategoryId(LocalDate.now(), categoryId))
                .thenReturn(Optional.empty());
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(challengeRepository.findLatestStartingScoreBefore(eq(categoryId), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(challengeRepository.findQuestionIdsUsedBetween(any(UUID.class), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(scorePicker.findViableQuestionAndScore(
                eq(categoryId), eq(-1), eq(LocalDate.now()), eq(List.of())))
                .thenReturn(new ChallengeScorePicker.QuestionScorePair(question, 301));
        when(challengeRepository.save(any(DailyChallenge.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DailyChallenge result = service.getTodaysChallenge(categoryId);

        assertThat(result).isNotNull();
        assertThat(result.getCategoryId()).isEqualTo(categoryId);
        assertThat(result.getQuestionId()).isEqualTo(questionId);
        assertThat(result.getChallengeDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void shouldThrowWhenNoViableQuestionExists() {
        when(challengeRepository.findByChallengeDateAndCategoryId(LocalDate.now(), categoryId))
                .thenReturn(Optional.empty());
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(challengeRepository.findLatestStartingScoreBefore(eq(categoryId), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(challengeRepository.findQuestionIdsUsedBetween(any(UUID.class), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(scorePicker.findViableQuestionAndScore(
                eq(categoryId), eq(-1), eq(LocalDate.now()), eq(List.of())))
                .thenReturn(null);

        assertThatThrownBy(() -> service.getTodaysChallenge(categoryId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("suitable_for_daily");
    }

    @Test
    void shouldReturnAllTodaysChallenges() {
        DailyChallenge footballChallenge = DailyChallenge.builder()
                .challengeDate(LocalDate.now()).categoryId(categoryId).startingScore(501).build();
        when(challengeRepository.findByChallengeDate(LocalDate.now()))
                .thenReturn(List.of(footballChallenge));

        List<DailyChallenge> results = service.getTodaysChallenges();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStartingScore()).isEqualTo(501);
    }

    @Test
    void shouldPickValidStartingScore() {
        int expectedScore = 301;
        when(scorePicker.pickStartingScore(categoryId)).thenReturn(expectedScore);

        int score = service.pickStartingScore(categoryId);

        assertThat(score).isEqualTo(expectedScore);
    }

    @Test
    void shouldResolveByCategorySlug() {
        when(categoryRepository.findBySlug("football")).thenReturn(Optional.of(category));
        when(challengeRepository.findByChallengeDateAndCategoryId(LocalDate.now(), categoryId))
                .thenReturn(Optional.empty());
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(challengeRepository.findLatestStartingScoreBefore(eq(categoryId), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(challengeRepository.findQuestionIdsUsedBetween(any(UUID.class), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(scorePicker.findViableQuestionAndScore(
                eq(categoryId), eq(-1), eq(LocalDate.now()), eq(List.of())))
                .thenReturn(new ChallengeScorePicker.QuestionScorePair(question, 301));
        when(challengeRepository.save(any(DailyChallenge.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DailyChallenge result = service.getTodaysChallenge("football");

        assertThat(result.getCategoryId()).isEqualTo(categoryId);
    }

    @Test
    void shouldThrowForUnknownCategorySlug() {
        when(categoryRepository.findBySlug("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTodaysChallenge("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category not found");
    }

    @Test
    @DisplayName("Should resume existing in-progress daily game instead of creating new one")
    void shouldResumeExistingInProgressDailyGame() {
        UUID playerId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        UUID existingQuestionId = UUID.randomUUID();

        DailyChallenge challenge = DailyChallenge.builder()
                .challengeDate(LocalDate.now()).categoryId(categoryId)
                .questionId(existingQuestionId).startingScore(301).status("active").build();

        Match existingMatch = Match.builder()
                .id(matchId).player1Id(playerId)
                .type(Match.MatchType.DAILY_CHALLENGE)
                .format(Match.MatchFormat.BEST_OF_1)
                .status(Match.MatchStatus.IN_PROGRESS)
                .player1GamesWon(0).build();

        Game existingGame = Game.builder()
                .id(gameId).matchId(matchId).gameNumber(1)
                .questionId(existingQuestionId)
                .status(Game.GameStatus.IN_PROGRESS)
                .currentTurnPlayerId(playerId)
                .player1Score(465).turnCount(1).build();

        Question existingQuestion = Question.builder()
                .id(existingQuestionId).categoryId(categoryId)
                .questionText("Existing daily question")
                .status(Question.STATUS_ACTIVE).difficultyScore(3.0).totalScorePool(600).build();

        // Stub the flow: category → challenge → no completed/abandoned → in-progress found
        when(categoryRepository.findBySlug("football")).thenReturn(Optional.of(category));
        when(challengeRepository.findByChallengeDateAndCategoryId(LocalDate.now(), categoryId))
                .thenReturn(Optional.of(challenge));

        // No completed game today
        when(gameRepository.findDailyGameByPlayerCategoryAndStatus(
                eq(playerId), eq(categoryId), eq(Match.MatchType.DAILY_CHALLENGE),
                eq(Game.GameStatus.COMPLETED), any(), any()))
                .thenReturn(Optional.empty());

        // No abandoned game today
        when(gameRepository.findDailyGameByPlayerCategoryAndStatus(
                eq(playerId), eq(categoryId), eq(Match.MatchType.DAILY_CHALLENGE),
                eq(Game.GameStatus.ABANDONED), any(), any()))
                .thenReturn(Optional.empty());

        // In-progress game exists — resume this one
        when(gameRepository.findDailyGameByPlayerCategoryAndStatus(
                eq(playerId), eq(categoryId), eq(Match.MatchType.DAILY_CHALLENGE),
                eq(Game.GameStatus.IN_PROGRESS), any(), any()))
                .thenReturn(Optional.of(existingGame));

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(existingMatch));
        when(questionRepository.findById(existingQuestionId)).thenReturn(Optional.of(existingQuestion));

        DailyChallengeService.GameStartRecord result = service.startDailyChallenge(playerId, "football");

        assertThat(result.game()).isEqualTo(existingGame);
        assertThat(result.match()).isEqualTo(existingMatch);
        assertThat(result.game().getPlayer1Score()).isEqualTo(465); // preserved score
        assertThat(result.game().getTurnCount()).isEqualTo(1);      // preserved turn count

        // Must NOT create a new game — the resume path skips matchService.createMatch + gameService.createGame
        verify(matchService, never()).createMatch(any(), any(), any(), any(), any());
        verify(gameService, never()).createGame(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Should throw when player already completed today's daily")
    void shouldThrowWhenDailyAlreadyCompleted() {
        UUID playerId = UUID.randomUUID();
        UUID completedGameId = UUID.randomUUID();

        DailyChallenge challenge = DailyChallenge.builder()
                .challengeDate(LocalDate.now()).categoryId(categoryId)
                .questionId(questionId).startingScore(301).status("active").build();

        Game completedGame = Game.builder()
                .id(completedGameId)
                .status(Game.GameStatus.COMPLETED).build();

        when(categoryRepository.findBySlug("football")).thenReturn(Optional.of(category));
        when(challengeRepository.findByChallengeDateAndCategoryId(LocalDate.now(), categoryId))
                .thenReturn(Optional.of(challenge));
        when(gameRepository.findDailyGameByPlayerCategoryAndStatus(
                eq(playerId), eq(categoryId), eq(Match.MatchType.DAILY_CHALLENGE),
                eq(Game.GameStatus.COMPLETED), any(), any()))
                .thenReturn(Optional.of(completedGame));

        assertThatThrownBy(() -> service.startDailyChallenge(playerId, "football"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already completed");
    }
}
