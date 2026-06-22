package com.trivia501.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Scoring Service Tests")
class ScoringServiceTest {

    private ScoringService scoringService;

    @BeforeEach
    void setUp() {
        scoringService = new ScoringService();
    }

    // ==========================================================================
    // VALID SCORING
    // ==========================================================================

    @Test
    @DisplayName("Valid score reduces current total")
    void shouldDeductScoreWhenValid() {
        int currentScore = 501;
        int answerScore = 36;

        ScoreResult result = scoringService.calculateScore(currentScore, answerScore);

        assertFalse(result.isBust());
        assertEquals(465, result.newScore());
    }

    @Test
    @DisplayName("Multiple valid scores deduct sequentially")
    void shouldDeductSequentially() {
        int score1 = scoringService.calculateScore(501, 60).newScore();
        assertEquals(441, score1);

        int score2 = scoringService.calculateScore(score1, 100).newScore();
        assertEquals(341, score2);

        int score3 = scoringService.calculateScore(score2, 41).newScore();
        assertEquals(300, score3);
    }

    @Test
    @DisplayName("Score of 1 is valid")
    void shouldAcceptMinimumValidScore() {
        ScoreResult result = scoringService.calculateScore(100, 1);

        assertFalse(result.isBust());
        assertEquals(99, result.newScore());
    }

    @Test
    @DisplayName("Score of 180 is valid")
    void shouldAcceptMaximumValidScore() {
        ScoreResult result = scoringService.calculateScore(501, 180);

        assertFalse(result.isBust());
        assertEquals(321, result.newScore());
    }

    // ==========================================================================
    // BUST SCENARIOS
    // ==========================================================================

    @ParameterizedTest
    @DisplayName("Invalid darts scores result in bust")
    @ValueSource(ints = {
        163, 166, 169, 172, 173, 175, 176, 178, 179, // Unachievable with 3 darts
        181, 200, 501,                               // > 180
        0, -1, -10                                   // <= 0
    })
    void shouldBustOnInvalidDartsScore(int invalidScore) {
        int currentScore = 501;
        ScoreResult result = scoringService.calculateScore(currentScore, invalidScore);

        assertTrue(result.isBust(), "Score " + invalidScore + " should be bust");
        assertEquals(currentScore, result.newScore());
    }

    @Test
    @DisplayName("Bust when resulting score is below checkout minimum (-10)")
    void shouldBustWhenResultIsTooLow() {
        // 15 - 30 = -15 (Too low)
        ScoreResult result = scoringService.calculateScore(15, 30);

        assertTrue(result.isBust());
        assertEquals(15, result.newScore());
    }

    @Test
    @DisplayName("Cannot score again once inside checkout range")
    void shouldBustIfAlreadyInCheckoutRange() {
        // Already at -5 (Winning position)
        ScoreResult result = scoringService.calculateScore(-5, 10);

        assertTrue(result.isBust());
        assertEquals(-5, result.newScore());
    }

    // ==========================================================================
    // CHECKOUT SCENARIOS
    // ==========================================================================

    @ParameterizedTest
    @DisplayName("Checkout range (-10 to 0) triggers win")
    @ValueSource(ints = {
        0,   // Exact 0
        -1,  // -1
        -5,  // -5
        -10  // Exact -10 (Limit)
    })
    void shouldCheckoutInRange(int targetScore) {
        // Calculate needed score to reach target
        int currentScore = 50;
        int answerScore = currentScore - targetScore;

        ScoreResult result = scoringService.calculateScore(currentScore, answerScore);

        assertFalse(result.isBust());
        assertTrue(result.isCheckout());
        assertEquals(targetScore, result.newScore());
    }

    @Test
    @DisplayName("Score resulting in -11 is bust (just outside range)")
    void shouldBustJustOutsideCheckoutRange() {
        int currentScore = 30;
        int answerScore = 41; // Result: -11

        ScoreResult result = scoringService.calculateScore(currentScore, answerScore);

        assertTrue(result.isBust());
        assertFalse(result.isCheckout());
        assertEquals(currentScore, result.newScore());
    }
}
