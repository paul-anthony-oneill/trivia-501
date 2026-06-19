package com.trivia501.repository;

import com.trivia501.model.DailyChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyChallengeRepository extends JpaRepository<DailyChallenge, UUID> {

    Optional<DailyChallenge> findByChallengeDateAndCategoryId(LocalDate challengeDate, UUID categoryId);

    List<DailyChallenge> findByChallengeDate(LocalDate challengeDate);

    Optional<DailyChallenge> findTopByCategoryIdOrderByChallengeDateDesc(UUID categoryId);

    List<DailyChallenge> findByChallengeDateAndStatus(LocalDate challengeDate, String status);

    /**
     * Returns the most recent daily challenge for a category (excluding today),
     * so the scheduler can avoid repeating yesterday's starting score.
     */
    @Query("""
        SELECT dc.startingScore FROM DailyChallenge dc
        WHERE dc.categoryId = :categoryId
          AND dc.challengeDate < :today
        ORDER BY dc.challengeDate DESC
        LIMIT 1
        """)
    Optional<Integer> findLatestStartingScoreBefore(
        @Param("categoryId") UUID categoryId,
        @Param("today") LocalDate today
    );

    /**
     * Returns question IDs used for a category within a date window
     * (inclusive start, exclusive end), for the 10-day cooldown check.
     */
    @Query("""
        SELECT dc.questionId FROM DailyChallenge dc
        WHERE dc.categoryId = :categoryId
          AND dc.challengeDate >= :since
          AND dc.challengeDate < :until
        """)
    List<UUID> findQuestionIdsUsedBetween(
        @Param("categoryId") UUID categoryId,
        @Param("since") LocalDate since,
        @Param("until") LocalDate until
    );

    /**
     * Returns the starting score the last time a specific question was used
     * as a daily challenge within a 90-day lookback window, so the anti-repeat
     * rule does not saturate as history accumulates.
     */
    @Query("""
        SELECT dc.startingScore FROM DailyChallenge dc
        WHERE dc.questionId = :questionId
          AND dc.challengeDate >= :since
        ORDER BY dc.challengeDate DESC
        LIMIT 1
        """)
    Optional<Integer> findLatestStartingScoreForQuestionSince(
        @Param("questionId") UUID questionId,
        @Param("since") LocalDate since
    );
}
