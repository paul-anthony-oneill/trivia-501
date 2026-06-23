package com.trivia501.engine;

import com.trivia501.model.Question;
import com.trivia501.repository.AnswerRepository;
import com.trivia501.repository.DailyChallengeRepository;
import com.trivia501.repository.QuestionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared score-picking and question-viability logic used by both
 * {@link com.trivia501.service.DailyChallengeService} and
 * {@link com.trivia501.scheduler.DailyChallengeScheduler}.
 *
 * <p>Extracted to break the static-method coupling where
 * {@code DailyChallengeService.createChallenge()} called
 * {@code DailyChallengeScheduler.findViableQuestionAndScore()}.
 */
@Component
@Slf4j
public class ChallengeScorePicker {

    /** Sentinel UUID for empty exclude lists — no real question has this UUID. */
    private static final UUID NO_EXCLUSIONS_SENTINEL = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final DailyChallengeRepository challengeRepository;

    public ChallengeScorePicker(
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            DailyChallengeRepository challengeRepository
    ) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.challengeRepository = challengeRepository;
    }

    /**
     * Finds a (question, score) pair where the question is viable for the given
     * starting score and hasn't been used recently.
     *
     * @param categoryId        the category to select for
     * @param yesterdayScore    the score used on the previous day, or -1
     * @param referenceDate     the challenge date
     * @param recentQuestionIds questions used in the cooldown window (excluded)
     * @return a viable pair, or null if exhausted
     */
    public QuestionScorePair findViableQuestionAndScore(
            UUID categoryId,
            int yesterdayScore,
            LocalDate referenceDate,
            List<UUID> recentQuestionIds
    ) {
        List<UUID> excludeIds = recentQuestionIds.isEmpty()
                ? List.of(NO_EXCLUSIONS_SENTINEL)
                : recentQuestionIds;

        int score = DifficultyConstants.pickDailyStartingScore(yesterdayScore);

        // Try with the random pick first
        QuestionScorePair result = tryPair(categoryId, score, referenceDate, excludeIds);
        if (result != null) return result;

        // Fallback: try every other score in the pool
        for (int s : DifficultyConstants.DAILY_STARTING_SCORES) {
            if (s == score) continue;
            result = tryPair(categoryId, s, referenceDate, excludeIds);
            if (result != null) return result;
        }

        // Soft-fallback: accept any viable pair even if score repeats
        for (int s : DifficultyConstants.DAILY_STARTING_SCORES) {
            Optional<Question> qOpt = findViableQuestion(categoryId, s, excludeIds);
            if (qOpt.isPresent()) {
                return new QuestionScorePair(qOpt.get(), s);
            }
        }

        return null;
    }

    /**
     * Picks a random starting score from the curated pool, optionally avoiding
     * the score used yesterday for the same category.
     */
    public int pickStartingScore(UUID categoryId) {
        int yesterday = challengeRepository
                .findLatestStartingScoreBefore(categoryId, LocalDate.now())
                .orElse(-1);
        return DifficultyConstants.pickDailyStartingScore(yesterday);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private QuestionScorePair tryPair(
            UUID categoryId,
            int score,
            LocalDate referenceDate,
            List<UUID> excludeIds
    ) {
        Optional<Question> qOpt = findViableQuestion(categoryId, score, excludeIds);
        if (qOpt.isEmpty()) return null;

        Question q = qOpt.get();
        LocalDate lookbackStart = referenceDate.minusDays(90);
        Integer lastScore = challengeRepository
                .findLatestStartingScoreForQuestionSince(q.getId(), lookbackStart)
                .orElse(null);
        if (lastScore != null && lastScore == score) {
            return null; // same question + same score within 90 days — try a different score
        }
        return new QuestionScorePair(q, score);
    }

    private Optional<Question> findViableQuestion(
            UUID categoryId,
            int startingScore,
            List<UUID> excludeIds
    ) {
        Optional<Question> questionOpt = questionRepository.findRandomDailyQuestion(
                categoryId, startingScore, excludeIds);
        if (questionOpt.isEmpty()) return Optional.empty();

        Question q = questionOpt.get();
        int firstMoveWindow = startingScore + DifficultyConstants.FIRST_MOVE_MARGIN;

        if (!answerRepository.hasViableFirstMove(q.getId(), firstMoveWindow)) {
            log.debug("Question {} (pool={}) has no first-move answers ≤ {} (startingScore={} + margin={})",
                    q.getId(), q.getTotalScorePool(), firstMoveWindow,
                    startingScore, DifficultyConstants.FIRST_MOVE_MARGIN);
            return Optional.empty();
        }

        return questionOpt;
    }

    /** Pair that bundles a question with its assigned starting score. */
    public record QuestionScorePair(Question question, int score) {}
}
