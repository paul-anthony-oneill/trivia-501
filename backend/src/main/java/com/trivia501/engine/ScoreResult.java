package com.trivia501.engine;

/**
 * Result of a scoring calculation.
 * ponytail: record replaces hand-rolled immutable class. Add back manual class if fields diverge.
 */
public record ScoreResult(int newScore, boolean isBust, boolean isCheckout, String reason) {

    public static ScoreResult bust(int currentScore, String reason) {
        return new ScoreResult(currentScore, true, false, reason);
    }

    public static ScoreResult checkout(int newScore) {
        return new ScoreResult(newScore, false, true, "Win!");
    }

    public static ScoreResult validScore(int newScore) {
        return new ScoreResult(newScore, false, false, null);
    }
}
