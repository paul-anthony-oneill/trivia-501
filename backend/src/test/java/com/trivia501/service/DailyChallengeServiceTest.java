package com.trivia501.service;

import com.trivia501.model.Category;
import com.trivia501.model.DailyChallenge;
import com.trivia501.model.Question;
import com.trivia501.repository.AnswerRepository;
import com.trivia501.repository.CategoryRepository;
import com.trivia501.repository.DailyChallengeRepository;
import com.trivia501.repository.GameRepository;
import com.trivia501.repository.MatchRepository;
import com.trivia501.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyChallengeServiceTest {

    @Mock private DailyChallengeRepository challengeRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private AnswerRepository answerRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private MatchService matchService;
    @Mock private GameService gameService;
    @Mock private GameRepository gameRepository;
    @Mock private MatchRepository matchRepository;

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
                challengeRepository, questionRepository, answerRepository,
                categoryRepository, matchService, gameService,
                gameRepository, matchRepository);
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
        when(challengeRepository.findLatestStartingScoreForQuestion(any(UUID.class)))
                .thenReturn(Optional.empty());
        when(questionRepository.findRandomDailyQuestion(eq(categoryId), anyInt(), anyList()))
                .thenReturn(Optional.of(question));
        when(answerRepository.hasViableFirstMove(eq(questionId), anyInt()))
                .thenReturn(true);
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
        // No question found for any score — all fallback attempts fail
        when(challengeRepository.findQuestionIdsUsedBetween(any(UUID.class), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(questionRepository.findRandomDailyQuestion(eq(categoryId), anyInt(), anyList()))
                .thenReturn(Optional.empty());

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
        when(challengeRepository.findLatestStartingScoreBefore(eq(categoryId), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        for (int i = 0; i < 50; i++) {
            int score = service.pickStartingScore(categoryId);
            assertThat(score).isIn((Object[]) java.util.Arrays.stream(
                com.trivia501.engine.DifficultyConstants.DAILY_STARTING_SCORES).boxed().toArray());
        }
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
        when(challengeRepository.findLatestStartingScoreForQuestion(any(UUID.class)))
                .thenReturn(Optional.empty());
        when(questionRepository.findRandomDailyQuestion(eq(categoryId), anyInt(), anyList()))
                .thenReturn(Optional.of(question));
        when(answerRepository.hasViableFirstMove(eq(questionId), anyInt()))
                .thenReturn(true);
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
}
