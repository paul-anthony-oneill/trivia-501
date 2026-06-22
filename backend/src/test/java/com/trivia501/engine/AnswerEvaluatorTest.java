package com.trivia501.engine;

import com.trivia501.model.Answer;
import com.trivia501.model.NamedEntity;
import com.trivia501.repository.AnswerRepository;
import com.trivia501.repository.NamedEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Tests for AnswerEvaluator.
 *
 * Answer resolution uses the entity ID from the autocomplete dropdown for
 * exact answer-key lookup. A fuzzy fallback fires only on exact-match miss.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Answer Evaluator Tests")
class AnswerEvaluatorTest {

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private NamedEntityRepository namedEntityRepository;

    @Mock
    private ScoringService scoringService;

    private AnswerEvaluator evaluator;

    private static final UUID QUESTION_ID = UUID.randomUUID();
    private static final UUID ANSWER_ID_HAALAND = UUID.randomUUID();
    private static final UUID ANSWER_ID_DE_BRUYNE = UUID.randomUUID();
    private static final UUID ENTITY_ID_HAALAND = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        evaluator = new AnswerEvaluator(answerRepository, namedEntityRepository, scoringService);
    }

    // ==========================================================================
    // ENTITY ID RESOLUTION
    // ==========================================================================

    @Test
    @DisplayName("Entity ID resolves to normalized name for exact answer-key lookup")
    void testEntityIdResolvesToAnswerKey() {
        Answer answer = createAnswer(
            ANSWER_ID_HAALAND, "Erling Haaland", 35, true, false
        );

        NamedEntity entity = NamedEntity.builder()
            .id(ENTITY_ID_HAALAND)
            .entityType("footballer")
            .displayName("Erling Haaland")
            .normalizedName("erling haaland")
            .build();

        when(namedEntityRepository.findById(ENTITY_ID_HAALAND))
            .thenReturn(Optional.of(entity));
        when(answerRepository.findByQuestionIdAndAnswerKey(QUESTION_ID, "erling haaland"))
            .thenReturn(Optional.of(answer));
        when(scoringService.calculateScore(501, 35))
            .thenReturn(ScoreResult.validScore(466));

        AnswerResult result = evaluator.evaluateAnswer(
            QUESTION_ID, "Erling Haaland", ENTITY_ID_HAALAND, 501, new ArrayList<>()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getDisplayText()).isEqualTo("Erling Haaland");
        assertThat(result.getScore()).isEqualTo(35);
        assertThat(result.getNewTotal()).isEqualTo(466);
        assertThat(result.isBust()).isFalse();
    }

    @Test
    @DisplayName("Fallback to raw text when entity ID resolves to nothing")
    void testEntityIdNotFound_fallsBackToRawText() {
        Answer answer = createAnswer(
            ANSWER_ID_HAALAND, "Erling Haaland", 35, true, false
        );

        when(namedEntityRepository.findById(ENTITY_ID_HAALAND))
            .thenReturn(Optional.empty());
        when(answerRepository.findByQuestionIdAndAnswerKey(QUESTION_ID, "erling haaland"))
            .thenReturn(Optional.of(answer));
        when(scoringService.calculateScore(501, 35))
            .thenReturn(ScoreResult.validScore(466));

        AnswerResult result = evaluator.evaluateAnswer(
            QUESTION_ID, "Erling Haaland", ENTITY_ID_HAALAND, 501, new ArrayList<>()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getDisplayText()).isEqualTo("Erling Haaland");
    }

    @Test
    @DisplayName("No entity ID uses normalized raw text for exact match")
    void testNoEntityId_usesNormalizedText() {
        Answer answer = createAnswer(
            ANSWER_ID_DE_BRUYNE, "Kevin De Bruyne", 28, true, false
        );

        when(answerRepository.findByQuestionIdAndAnswerKey(QUESTION_ID, "kevin de bruyne"))
            .thenReturn(Optional.of(answer));
        when(scoringService.calculateScore(501, 28))
            .thenReturn(ScoreResult.validScore(473));

        AnswerResult result = evaluator.evaluateAnswer(
            QUESTION_ID, "KEVIN DE BRUYNE", null, 501, new ArrayList<>()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getDisplayText()).isEqualTo("Kevin De Bruyne");
    }

    // ==========================================================================
    // INVALID ANSWERS
    // ==========================================================================

    @Test
    @DisplayName("Answer not found returns invalid after exact and fuzzy both miss")
    void testAnswerNotFound() {
        when(answerRepository.findByQuestionIdAndAnswerKey(any(), any()))
            .thenReturn(Optional.empty());
        when(answerRepository.findFuzzyMatch(any(), any(), anyDouble()))
            .thenReturn(Optional.empty());

        AnswerResult result = evaluator.evaluateAnswer(
            QUESTION_ID, "Lionel Messi", null, 501, new ArrayList<>()
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.getReason()).isEqualTo("Answer not found");
    }

    @Test
    @DisplayName("Fuzzy fallback catches typo when exact match misses")
    void testFuzzyFallbackOnTypo() {
        Answer answer = createAnswer(
            ANSWER_ID_HAALAND, "Erling Haaland", 35, true, false
        );

        when(answerRepository.findByQuestionIdAndAnswerKey(QUESTION_ID, "erling haland"))
            .thenReturn(Optional.empty());
        when(answerRepository.findFuzzyMatch(QUESTION_ID, "erling haland", 0.5))
            .thenReturn(Optional.of(answer));
        when(scoringService.calculateScore(501, 35))
            .thenReturn(ScoreResult.validScore(466));

        AnswerResult result = evaluator.evaluateAnswer(
            QUESTION_ID, "Erling Haland", null, 501, new ArrayList<>()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getDisplayText()).isEqualTo("Erling Haaland");
    }

    @Test
    @DisplayName("Empty answer returns invalid")
    void testEmptyAnswer() {
        AnswerResult result = evaluator.evaluateAnswer(
            QUESTION_ID, "", null, 501, new ArrayList<>()
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.getReason()).isEqualTo("Empty answer");
    }

    @Test
    @DisplayName("Already used answer returns invalid")
    void testAlreadyUsedAnswer() {
        List<UUID> usedAnswers = List.of(ANSWER_ID_HAALAND);

        Answer answer = createAnswer(
            ANSWER_ID_HAALAND, "Erling Haaland", 35, true, false
        );

        when(answerRepository.findByQuestionIdAndAnswerKey(QUESTION_ID, "erling haaland"))
            .thenReturn(Optional.of(answer));

        AnswerResult result = evaluator.evaluateAnswer(
            QUESTION_ID, "Erling Haaland", null, 501, usedAnswers
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.getReason()).isEqualTo("Answer already used");
    }

    // ==========================================================================
    // DARTS SCORE VALIDATION
    // ==========================================================================

    @Test
    @DisplayName("Invalid darts score 179 results in bust")
    void testInvalidDartsScore179() {
        Answer answer = createAnswer(
            UUID.randomUUID(), "Jack Grealish", 179, false, false
        );

        when(answerRepository.findByQuestionIdAndAnswerKey(any(), any()))
            .thenReturn(Optional.of(answer));
        when(scoringService.calculateScore(501, 179))
            .thenReturn(ScoreResult.bust(501, "179 is not a valid 3-dart checkout score"));

        AnswerResult result = evaluator.evaluateAnswer(
            QUESTION_ID, "Jack Grealish", null, 501, new ArrayList<>()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.isBust()).isTrue();
        assertThat(result.getNewTotal()).isEqualTo(501);
    }

    @Test
    @DisplayName("Score over 180 results in bust")
    void testScoreOver180Bust() {
        Answer answer = createAnswer(
            UUID.randomUUID(), "John Stones", 200, false, true
        );

        when(answerRepository.findByQuestionIdAndAnswerKey(any(), any()))
            .thenReturn(Optional.of(answer));
        when(scoringService.calculateScore(501, 200))
            .thenReturn(ScoreResult.bust(501, "200 is not a valid 3-dart checkout score"));

        AnswerResult result = evaluator.evaluateAnswer(
            QUESTION_ID, "John Stones", null, 501, new ArrayList<>()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.isBust()).isTrue();
        assertThat(result.getScore()).isEqualTo(200);
    }

    @Test
    @DisplayName("Valid max darts score 180 works")
    void testValidDartsScoreMax() {
        Answer answer = createAnswer(
            UUID.randomUUID(), "Kyle Walker", 180, true, false
        );

        when(answerRepository.findByQuestionIdAndAnswerKey(any(), any()))
            .thenReturn(Optional.of(answer));
        when(scoringService.calculateScore(501, 180))
            .thenReturn(ScoreResult.validScore(321));

        AnswerResult result = evaluator.evaluateAnswer(
            QUESTION_ID, "Kyle Walker", null, 501, new ArrayList<>()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.isBust()).isFalse();
        assertThat(result.getNewTotal()).isEqualTo(321);
        assertThat(result.isValidDartsScore()).isTrue();
    }

    // ==========================================================================
    // WIN CONDITIONS (CHECKOUT)
    // ==========================================================================

    @Test
    @DisplayName("Exact checkout at 0 triggers win")
    void testExactCheckoutZero() {
        Answer answer = createAnswer(
            UUID.randomUUID(), "Ederson", 10, true, false
        );

        when(answerRepository.findByQuestionIdAndAnswerKey(any(), any()))
            .thenReturn(Optional.of(answer));
        when(scoringService.calculateScore(10, 10))
            .thenReturn(ScoreResult.checkout(0));

        AnswerResult result = evaluator.evaluateAnswer(
            QUESTION_ID, "Ederson", null, 10, new ArrayList<>()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.isWin()).isTrue();
        assertThat(result.getNewTotal()).isEqualTo(0);
        assertThat(result.getReason()).isEqualTo("Win!");
    }

    @Test
    @DisplayName("Checkout at -5 triggers win")
    void testCheckoutNegative5() {
        Answer answer = createAnswer(
            UUID.randomUUID(), "Phil Foden", 5, true, false
        );

        when(answerRepository.findByQuestionIdAndAnswerKey(any(), any()))
            .thenReturn(Optional.of(answer));
        when(scoringService.calculateScore(0, 5))
            .thenReturn(ScoreResult.checkout(-5));

        AnswerResult result = evaluator.evaluateAnswer(
            QUESTION_ID, "Phil Foden", null, 0, new ArrayList<>()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.isWin()).isTrue();
        assertThat(result.getNewTotal()).isEqualTo(-5);
    }

    @Test
    @DisplayName("Below checkout range results in bust")
    void testBelowCheckoutRangeBust() {
        Answer answer = createAnswer(
            UUID.randomUUID(), "Erling Haaland", 35, true, false
        );

        when(answerRepository.findByQuestionIdAndAnswerKey(any(), any()))
            .thenReturn(Optional.of(answer));
        when(scoringService.calculateScore(20, 35))
            .thenReturn(ScoreResult.bust(20, "Would drop below -10 (bust)"));

        AnswerResult result = evaluator.evaluateAnswer(
            QUESTION_ID, "Erling Haaland", null, 20, new ArrayList<>()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.isBust()).isTrue();
        assertThat(result.getNewTotal()).isEqualTo(20);
    }

    // ==========================================================================
    // UTILITY METHODS
    // ==========================================================================

    @Test
    @DisplayName("Get available answer count works")
    void testGetAvailableAnswerCount() {
        when(answerRepository.countAvailableAnswers(QUESTION_ID, null))
            .thenReturn(10L);

        long count = evaluator.getAvailableAnswerCount(QUESTION_ID, new ArrayList<>());

        assertThat(count).isEqualTo(10);
    }

    @Test
    @DisplayName("Get top answers returns sorted list")
    void testGetTopAnswers() {
        List<Answer> topAnswers = List.of(
            createAnswer(UUID.randomUUID(), "Player 1", 180, true, false),
            createAnswer(UUID.randomUUID(), "Player 2", 150, true, false),
            createAnswer(UUID.randomUUID(), "Player 3", 100, true, false)
        );

        when(answerRepository.findTopAnswers(QUESTION_ID, true, 3))
            .thenReturn(topAnswers);

        List<Answer> results = evaluator.getTopAnswers(QUESTION_ID, 3, true);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getScore()).isEqualTo(180);
    }

    @Test
    @DisplayName("Get answer stats returns correct counts")
    void testGetAnswerStats() {
        when(answerRepository.countByQuestionId(QUESTION_ID)).thenReturn(10L);
        when(answerRepository.countByQuestionIdAndIsValidDartsTrue(QUESTION_ID)).thenReturn(7L);

        AnswerEvaluator.AnswerStats stats = evaluator.getAnswerStats(QUESTION_ID);

        assertThat(stats.totalAnswers()).isEqualTo(10);
        assertThat(stats.validDartsAnswers()).isEqualTo(7);
        assertThat(stats.invalidOrBustAnswers()).isEqualTo(3);
    }

    // ==========================================================================
    // HELPER METHODS
    // ==========================================================================

    private Answer createAnswer(
        UUID id,
        String displayText,
        int score,
        boolean isValidDarts,
        boolean isBust
    ) {
        return Answer.builder()
            .id(id)
            .questionId(QUESTION_ID)
            .answerKey(displayText.toLowerCase())
            .displayText(displayText)
            .score(score)
            .isValidDarts(isValidDarts)
            .isBust(isBust)
            .build();
    }
}
