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

    private static final UUID NIL_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

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
     */
    @Scheduled(cron = "0 0 0 1 * *")
    @Transactional
    public void selectDailyChallenges() {
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
                    // Skip if challenge already exists for this day
                    if (challengeRepository.findByChallengeDateAndCategoryId(date, category.getId()).isPresent()) {
                        totalSkipped++;
                        continue;
                    }

                    // Questions used in the cooldown window [date-10, date)
                    LocalDate cooldownStart = date.minusDays(DifficultyConstants.DAILY_QUESTION_COOLDOWN_DAYS);
                    List<UUID> recentQuestionIds = challengeRepository.findQuestionIdsUsedBetween(
                            category.getId(), cooldownStart, date);

                    // Yesterday's score for this category (anti-consecutive-repeat)
                    int yesterdayScore = challengeRepository
                            .findLatestStartingScoreBefore(category.getId(), date)
                            .orElse(-1);

                    var result = findViableQuestionAndScore(
                            questionRepository, answerRepository, challengeRepository,
                            category.getId(), yesterdayScore, recentQuestionIds);

                    if (result == null) {
                        log.warn("No viable question for '{}' on {} at any score (cooldown window: {} days)",
                                category.getSlug(), date,
                                DifficultyConstants.DAILY_QUESTION_COOLDOWN_DAYS);
                        totalFailed++;
                        continue;
                    }

                    DailyChallenge challenge = DailyChallenge.builder()
                            .challengeDate(date)
                            .categoryId(category.getId())
                            .questionId(result.question().getId())
                            .startingScore(result.score())
                            .status("active")
                            .build();

                    challengeRepository.save(challenge);
                    totalCreated++;
                    log.debug("Daily challenge: {} / {} / q={} score={}",
                            date, category.getSlug(), result.question().getId(), result.score());

                } catch (Exception e) {
                    log.error("Failed for '{}' on {}", category.getSlug(), date, e);
                    totalFailed++;
                }
            }
        }

        log.info("Monthly generation complete: created={}, skipped={}, failed={}", totalCreated, totalSkipped, totalFailed);
    }

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
            List<UUID>                recentQuestionIds
    ) {
        List<UUID> excludeIds = recentQuestionIds.isEmpty()
                ? List.of(NIL_UUID)
                : recentQuestionIds;

        int score = DifficultyConstants.pickDailyStartingScore(yesterdayScore);

        // Try with the random pick first
        QuestionScorePair result = tryPair(questionRepo, answerRepo, challengeRepo,
                categoryId, score, excludeIds);
        if (result != null) return result;

        // Fallback: try every other score in the pool
        for (int s : DifficultyConstants.DAILY_STARTING_SCORES) {
            if (s == score) continue;
            result = tryPair(questionRepo, answerRepo, challengeRepo,
                    categoryId, s, excludeIds);
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
     *  or if the score would repeat for the selected question. */
    private static QuestionScorePair tryPair(
            QuestionRepository        questionRepo,
            AnswerRepository          answerRepo,
            DailyChallengeRepository  challengeRepo,
            UUID                      categoryId,
            int                       score,
            List<UUID>                excludeIds
    ) {
        Optional<Question> qOpt = findViableQuestion(
                questionRepo, answerRepo, categoryId, score, excludeIds);
        if (qOpt.isEmpty()) return null;

        Question q = qOpt.get();
        Integer lastScore = challengeRepo
                .findLatestStartingScoreForQuestion(q.getId()).orElse(null);
        if (lastScore != null && lastScore == score) {
            return null; // same question + same score — try a different score
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
