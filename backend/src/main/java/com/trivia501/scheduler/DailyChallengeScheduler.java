package com.trivia501.scheduler;

import com.trivia501.engine.ChallengeScorePicker;
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
    private final ChallengeScorePicker scorePicker;

    public DailyChallengeScheduler(
            DailyChallengeRepository challengeRepository,
            QuestionRepository questionRepository,
            CategoryRepository categoryRepository,
            AnswerRepository answerRepository,
            ChallengeScorePicker scorePicker
    ) {
        this.challengeRepository = challengeRepository;
        this.questionRepository = questionRepository;
        this.categoryRepository = categoryRepository;
        this.answerRepository = answerRepository;
        this.scorePicker = scorePicker;
    }

    /**
     * Daily catch-up at 00:05 UTC: ensures today's challenge exists for every
     * category with eligible questions. Idempotent — generateOneDay skips
     * existing rows. Covers the case where the monthly cron was missed (Fly
     * machine asleep, crash, deploy window).
     */
    @Scheduled(cron = "0 5 0 * * *")
    public void ensureTodaysChallenges() {
        LocalDate today = LocalDate.now();
        log.info("Daily catch-up: ensuring challenges exist for {}", today);

        List<Category> categories = categoryRepository.findAll();
        int created = 0;
        int skipped = 0;
        int failed = 0;

        for (Category category : categories) {
            if ("test".equals(category.getSlug())) continue;
            if (!questionRepository.existsByCategoryIdAndSuitableForDailyTrueAndStatus(
                    category.getId(), Question.STATUS_ACTIVE)) continue;

            try {
                int outcome = generateOneDay(category, today);
                switch (outcome) {
                    case 1 -> created++;
                    case 0 -> skipped++;
                    case -1 -> failed++;
                }
            } catch (Exception e) {
                log.error("Daily catch-up failed for '{}' on {}", category.getSlug(), today, e);
                failed++;
            }
        }

        log.info("Daily catch-up complete: created={}, skipped={}, failed={}", created, skipped, failed);
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

        var result = scorePicker.findViableQuestionAndScore(
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
}
