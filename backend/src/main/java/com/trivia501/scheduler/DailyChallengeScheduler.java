package com.trivia501.scheduler;

import com.trivia501.engine.DifficultyConstants;
import com.trivia501.model.Category;
import com.trivia501.model.DailyChallenge;
import com.trivia501.model.Question;
import com.trivia501.repository.AnswerRepository;
import com.trivia501.repository.CategoryRepository;
import com.trivia501.repository.DailyChallengeRepository;
import com.trivia501.repository.QuestionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pre-selects daily challenges at midnight on the 1st of each month so all
 * days of the upcoming month are ready when players arrive.  Each day gets
 * a question + starting score per category with a 10-day question cooldown.
 *
 * <p>Idempotent — if a challenge already exists for a day, it is skipped.
 */
@Component
@Slf4j
public class DailyChallengeScheduler {

    /** Sentinel UUID passed to {@code NOT IN (:excludeIds)} when the exclude
     *  list is empty — no real question has this UUID, so the clause is a no-op.
     *  Required because PostgreSQL {@code NOT IN (empty_list)} returns no rows. */
    private static final UUID NO_EXCLUSIONS_SENTINEL = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final DailyChallengeRepository challengeRepository;
    private final QuestionRepository questionRepository;
    private final CategoryRepository categoryRepository;
    private final AnswerRepository answerRepository;

    public DailyChallengeScheduler(
            DailyChallengeRepository challengeRepository,
            QuestionRepository questionRepository,
            CategoryRepository categoryRepository,
            AnswerRepository answerRepository
    ) {
        this.challengeRepository = challengeRepository;
        this.questionRepository = questionRepository;
        this.categoryRepository = categoryRepository;
        this.answerRepository = answerRepository;
    }

    /**
     * Runs at midnight UTC on the 1st of every month. Generates daily
     * challenges for every day of the current month, for every category
     * that has eligible questions.
     *
     * @return summary counts (created, skipped, failed)
     */
    @Scheduled(cron = "0 0 0 1 * *")
    public GenerationSummary selectDailyChallenges() {
        YearMonth month = YearMonth.now();
        LocalDate firstOfMonth = month.atDay(1);
        LocalDate lastOfMonth = month.atEndOfMonth();
        log.info("Monthly daily challenge generation starting for {} ({}–{})",
                month, firstOfMonth, lastOfMonth);

        List<Category> categories = categoryRepository.findAll();
        int totalCreated = 0;
        int totalSkipped = 0;
        int totalFailed = 0;

        for (Category category : categories) {
            if ("test".equals(category.getSlug())) continue;

            if (!questionRepository.existsByCategoryIdAndSuitableForDailyTrueAndStatus(
                    category.getId(), Question.STATUS_ACTIVE)) {
                log.debug("No suitable_for_daily questions for '{}', skipping", category.getSlug());
                continue;
            }

            for (LocalDate date = firstOfMonth; !date.isAfter(lastOfMonth); date = date.plusDays(1)) {
                try {
                    int outcome = generateOneDay(category, date);
                    switch (outcome) {
                        case 1 -> totalCreated++;
                        case 0 -> totalSkipped++;
                        case -1 -> totalFailed++;
                    }
                } catch (Exception e) {
                    log.error("Failed for '{}' on {}", category.getSlug(), date, e);
                    totalFailed++;
                }
            }
        }

        log.info("Monthly generation complete: created={}, skipped={}, failed={}", totalCreated, totalSkipped, totalFailed);
        return new GenerationSummary(totalCreated, totalSkipped, totalFailed);
    }

    /**
     * Generates the daily challenge for a single (category, date) pair.
     * Runs in its own transaction so a failure on one day does not roll
     * back previously-created challenges.
     *
     * @return 1 = created, 0 = skipped (exists), -1 = failed (no viable question)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private int generateOneDay(Category category, LocalDate date) {
        if (challengeRepository.findByChallengeDateAndCategoryId(date, category.getId()).isPresent()) {
            return 0; // skipped — already exists
        }

        LocalDate cooldownStart = date.minusDays(DifficultyConstants.DAILY_QUESTION_COOLDOWN_DAYS);
        List<UUID> recentQuestionIds = challengeRepository.findQuestionIdsUsedBetween(
                category.getId(), cooldownStart, date);

        int yesterdayScore = challengeRepository
                .findLatestStartingScoreBefore(category.getId(), date)
                .orElse(-1);

        var result = findViableQuestionAndScore(
                questionRepository, answerRepository, challengeRepository,
                category.getId(), yesterdayScore, date, recentQuestionIds);

        if (result == null) {
            log.warn("No viable question for '{}' on {} at any score (cooldown window: {} days)",
                    category.getSlug(), date,
                    DifficultyConstants.DAILY_QUESTION_COOLDOWN_DAYS);
            return -1;
        }

        DailyChallenge challenge = DailyChallenge.builder()
                .challengeDate(date)
                .categoryId(category.getId())
                .questionId(result.question().getId())
                .startingScore(result.score())
                .status("active")
                .build();

        challengeRepository.save(challenge);
        log.debug("Daily challenge: {} / {} / q={} score={}",
                date, category.getSlug(), result.question().getId(), result.score());
        return 1;
    }

    public record GenerationSummary(int created, int skipped, int failed) {}

    // ── Question selection (static — shared with DailyChallengeService) ──────

    /**
     * Finds a (question, score) pair where the question is viable for the given
     * starting score and hasn't been used recently.
     *
     * @param yesterdayScore    the score used on the previous day for this category, or -1
     * @param recentQuestionIds questions used in the last 10 days (will be excluded)
     * @return a viable pair, or null if exhausted
     */
    public static QuestionScorePair findViableQuestionAndScore(
            QuestionRepository        questionRepo,
            AnswerRepository          answerRepo,
            DailyChallengeRepository  challengeRepo,
            UUID                      categoryId,
            int                       yesterdayScore,
            LocalDate                 referenceDate,
            List<UUID>                recentQuestionIds
    ) {
        List<UUID> excludeIds = recentQuestionIds.isEmpty()
                ? List.of(NO_EXCLUSIONS_SENTINEL)
                : recentQuestionIds;

        int score = DifficultyConstants.pickDailyStartingScore(yesterdayScore);

        // Try with the random pick first
        QuestionScorePair result = tryPair(questionRepo, answerRepo, challengeRepo,
                categoryId, score, referenceDate, excludeIds);
        if (result != null) return result;

        // Fallback: try every other score in the pool
        for (int s : DifficultyConstants.DAILY_STARTING_SCORES) {
            if (s == score) continue;
            result = tryPair(questionRepo, answerRepo, challengeRepo,
                    categoryId, s, referenceDate, excludeIds);
            if (result != null) return result;
        }

        // Soft-fallback: accept any viable pair even if score repeats
        for (int s : DifficultyConstants.DAILY_STARTING_SCORES) {
            Optional<Question> qOpt = findViableQuestion(
                    questionRepo, answerRepo, categoryId, s, excludeIds);
            if (qOpt.isPresent()) {
                return new QuestionScorePair(qOpt.get(), s);
            }
        }

        return null;
    }

    /** Tries one (score) candidate; returns null if no viable question exists
     *  or if the score would repeat for the selected question within 90 days. */
    private static QuestionScorePair tryPair(
            QuestionRepository        questionRepo,
            AnswerRepository          answerRepo,
            DailyChallengeRepository  challengeRepo,
            UUID                      categoryId,
            int                       score,
            LocalDate                 referenceDate,
            List<UUID>                excludeIds
    ) {
        Optional<Question> qOpt = findViableQuestion(
                questionRepo, answerRepo, categoryId, score, excludeIds);
        if (qOpt.isEmpty()) return null;

        Question q = qOpt.get();
        LocalDate lookbackStart = referenceDate.minusDays(90);
        Integer lastScore = challengeRepo
                .findLatestStartingScoreForQuestionSince(q.getId(), lookbackStart)
                .orElse(null);
        if (lastScore != null && lastScore == score) {
            return null; // same question + same score within 90 days — try a different score
        }
        return new QuestionScorePair(q, score);
    }

    private static Optional<Question> findViableQuestion(
            QuestionRepository questionRepo,
            AnswerRepository   answerRepo,
            UUID               categoryId,
            int                startingScore,
            List<UUID>         excludeIds
    ) {
        Optional<Question> questionOpt = questionRepo.findRandomDailyQuestion(
                categoryId, startingScore, excludeIds);
        if (questionOpt.isEmpty()) return Optional.empty();

        Question q = questionOpt.get();
        int firstMoveWindow = startingScore + DifficultyConstants.FIRST_MOVE_MARGIN;

        if (!answerRepo.hasViableFirstMove(q.getId(), firstMoveWindow)) {
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
