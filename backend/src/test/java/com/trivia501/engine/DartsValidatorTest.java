package com.trivia501.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Darts Validator Tests")
class DartsValidatorTest {

    @ParameterizedTest
    @DisplayName("Invalid darts scores return false")
    @ValueSource(ints = {
        163, 166, 169, 172, 173, 175, 176, 178, 179, // Not achievable with 3 darts
        181, 200,                                    // > 180
        0, -1, -10                                   // <= 0
    })
    void shouldReturnFalseForInvalidScores(int score) {
        assertFalse(DartsValidator.isValidDartsScore(score),
            "Score " + score + " should be invalid");
    }

    @ParameterizedTest
    @DisplayName("Valid darts scores return true")
    @ValueSource(ints = {
        1, 20, 60, 100, 120, 140, 160, 164, 165, 167, 168, 170, 171, 174, 177, 180
    })
    void shouldReturnTrueForValidScores(int score) {
        assertTrue(DartsValidator.isValidDartsScore(score),
            "Score " + score + " should be valid");
    }

    @Test
    @DisplayName("Score 0 is invalid — cannot score 0 points with 3 darts; 0 only appears as checkout result")
    void testScoreZeroIsInvalid() {
        assertFalse(DartsValidator.isValidDartsScore(0),
            "Score 0 must be invalid as a darts score. It only appears as the result of a checkout "
            + "(reaching exactly 0), not as a throwable score. "
            + "The Python scraper agrees: utils/darts.py rejects score < 1.");
    }

    @Test
    @DisplayName("Exactly 171 scores are valid (1-180 minus the 9 impossible ones)")
    void testValidScoreCount() {
        long validCount = java.util.stream.IntStream.rangeClosed(0, 180)
            .filter(DartsValidator::isValidDartsScore)
            .count();
        assertTrue(validCount == 171,
            () -> "Expected 171 valid scores (1-180 minus 9 impossible), got " + validCount);
    }
}
