package com.trivia501.repository;

import com.trivia501.model.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Answer entity.
 * Provides fuzzy matching using PostgreSQL trigram similarity.
 */
@Repository
public interface AnswerRepository extends JpaRepository<Answer, UUID> {

    /**
     * Find valid answer by question and exact answer key match.
     *
     * @param questionId the question UUID
     * @param answerKey the normalized answer key (lowercase)
     * @return optional answer
     */
    Optional<Answer> findByQuestionIdAndAnswerKey(UUID questionId, String answerKey);

    List<Answer> findByQuestionIdAndAnswerKeyIn(UUID questionId, java.util.Set<String> answerKeys);

    /**
     * Accent-insensitive exact lookup — used as a fallback when the standard
     * exact match misses, to handle discrepancies between Java NFD-based accent
     * stripping and the Python scraper's {@code str.lower()} which preserves accents.
     */
    @Query(value = """
        SELECT * FROM answers
        WHERE question_id = :questionId
          AND regexp_replace(unaccent(answer_key), '[^a-z0-9]', '', 'g')
            = regexp_replace(unaccent(:answerKey), '[^a-z0-9]', '', 'g')
        LIMIT 1
        """, nativeQuery = true)
    Optional<Answer> findByQuestionIdAndAnswerKeyNormalised(
        @Param("questionId") UUID questionId,
        @Param("answerKey") String answerKey
    );

    /**
     * Fuzzy-match fallback — only called when the exact answer-key lookup misses.
     * Uses PostgreSQL {@code similarity()} with a GIN trigram index to catch
     * typos, accent differences, and minor formatting drift.
     *
     * <p>Exclusion of already-used answers is checked in Java after the result
     * returns, avoiding the NULL/empty-list type-inference failure in Hibernate 7
     * + PostgreSQL that the old two-variant native queries worked around.
     */
    @Query(value = """
        SELECT *,
               similarity(answer_key, :normalizedInput) as sim
        FROM answers
        WHERE question_id = :questionId
          AND similarity(answer_key, :normalizedInput) >= :threshold
        ORDER BY sim DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<Answer> findFuzzyMatch(
        @Param("questionId") UUID questionId,
        @Param("normalizedInput") String normalizedInput,
        @Param("threshold") double threshold
    );

    /**
     * Count available answers for a question (excluding used answers).
     *
     * @param questionId the question UUID
     * @param usedAnswerIds list of already-used answer IDs
     * @return count of available answers
     */
    @Query("""
        SELECT COUNT(a)
        FROM Answer a
        WHERE a.questionId = :questionId
          AND (:usedAnswerIds IS NULL OR a.id NOT IN :usedAnswerIds)
        """)
    long countAvailableAnswers(
        @Param("questionId") UUID questionId,
        @Param("usedAnswerIds") List<UUID> usedAnswerIds
    );

    /**
     * Get top scoring answers for a question.
     *
     * @param questionId the question UUID
     * @param excludeInvalidDarts whether to exclude invalid darts scores
     * @return list of top answers
     */
    @Query("""
        SELECT a
        FROM Answer a
        WHERE a.questionId = :questionId
          AND (:excludeInvalidDarts = false OR a.isValidDarts = true)
        ORDER BY a.score DESC
        """)
    List<Answer> findTopAnswers(
        @Param("questionId") UUID questionId,
        @Param("excludeInvalidDarts") boolean excludeInvalidDarts
    );

    /**
     * Get top N scoring answers for a question, optionally excluding invalid darts scores.
     */
    @Query("""
        SELECT a
        FROM Answer a
        WHERE a.questionId = :questionId
          AND (:excludeInvalidDarts = false OR a.isValidDarts = true)
        ORDER BY a.score DESC
        LIMIT :limit
        """)
    List<Answer> findTopAnswers(
        @Param("questionId") UUID questionId,
        @Param("excludeInvalidDarts") boolean excludeInvalidDarts,
        @Param("limit") int limit
    );

    /**
     * Count total answers for a question.
     *
     * @param questionId the question UUID
     * @return total answer count
     */
    long countByQuestionId(UUID questionId);

    /**
     * Count valid darts scores for a question.
     *
     * @param questionId the question UUID
     * @return count of valid darts scores
     */
    long countByQuestionIdAndIsValidDartsTrue(UUID questionId);

    /**
     * Sum of scores for all valid, non-bust answers for a question.
     * This is the total "points pool" — the maximum achievable score if a
     * player named every valid answer correctly. A pool below 501 means the
     * question literally cannot be finished from 501 points.
     *
     * @param questionId the question UUID
     * @return sum of valid-darts scores, or 0 if no answers exist
     */
    @Query("SELECT COALESCE(SUM(a.score), 0) FROM Answer a " +
           "WHERE a.questionId = :questionId AND a.isValidDarts = true AND a.isBust = false")
    long sumValidDartsScores(@Param("questionId") UUID questionId);

    /**
     * Count of valid, non-bust answers with a score above 100.
     * A higher count here means the question has strong "finishing power" —
     * players can make large progress per correct answer.
     *
     * @param questionId the question UUID
     * @return count of high-value answers (score > 100)
     */
    @Query("SELECT COUNT(a) FROM Answer a " +
           "WHERE a.questionId = :questionId AND a.isValidDarts = true AND a.isBust = false AND a.score > 100")
    long countHighValueAnswers(@Param("questionId") UUID questionId);

    /**
     * Get top N scoring answers for a question.
     *
     * @param questionId the question UUID
     * @param limit maximum number of results
     * @return list of top N answers ordered by score descending
     */
    @Query(value = """
        SELECT *
        FROM answers
        WHERE question_id = :questionId
        ORDER BY score DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Answer> findTopNByQuestionIdOrderByScoreDesc(
        @Param("questionId") UUID questionId,
        @Param("limit") int limit
    );

    List<Answer> findByQuestionIdOrderByScoreDesc(UUID questionId);
    boolean existsByQuestionIdAndAnswerKey(UUID questionId, String answerKey);

    @Query("SELECT a.answerKey FROM Answer a WHERE a.questionId = :questionId")
    java.util.Set<String> findAnswerKeysByQuestionId(@Param("questionId") UUID questionId);

    // ── In-game hint queries ──────────────────────────────────────────────────
    //
    // Two hint types are computed after every move:
    //   maxScoresLeft  — remaining answers worth exactly 180 (max darts score)
    //   checkoutsLeft  — remaining answers that would end the game in one move
    //
    // Each query has a pair (no-exclusion / with-exclusion) to avoid the
    // NULL/empty-list type-inference failure in Hibernate 7 + PostgreSQL.
    // The default dispatch methods pick the right variant automatically.

    @Query("SELECT COUNT(a) FROM Answer a " +
           "WHERE a.questionId = :questionId " +
           "AND a.score = 180 AND a.isValidDarts = true AND a.isBust = false")
    long countMaxScoreAnswers(@Param("questionId") UUID questionId);

    @Query("SELECT COUNT(a) FROM Answer a " +
           "WHERE a.questionId = :questionId " +
           "AND a.score = 180 AND a.isValidDarts = true AND a.isBust = false " +
           "AND a.id NOT IN :usedIds")
    long countMaxScoreAnswersExcluding(
        @Param("questionId") UUID questionId,
        @Param("usedIds") List<UUID> usedIds
    );

    @Query("SELECT COUNT(a) FROM Answer a " +
           "WHERE a.questionId = :questionId " +
           "AND a.score >= :minScore AND a.score <= :maxScore " +
           "AND a.isValidDarts = true AND a.isBust = false")
    long countCheckoutAnswers(
        @Param("questionId") UUID questionId,
        @Param("minScore") int minScore,
        @Param("maxScore") int maxScore
    );

    @Query("SELECT COUNT(a) FROM Answer a " +
           "WHERE a.questionId = :questionId " +
           "AND a.score >= :minScore AND a.score <= :maxScore " +
           "AND a.isValidDarts = true AND a.isBust = false " +
           "AND a.id NOT IN :usedIds")
    long countCheckoutAnswersExcluding(
        @Param("questionId") UUID questionId,
        @Param("minScore") int minScore,
        @Param("maxScore") int maxScore,
        @Param("usedIds") List<UUID> usedIds
    );

    /** Dispatches to the correct max-score query based on whether usedIds is empty. */
    default long countRemainingMaxScores(UUID questionId, List<UUID> usedIds) {
        if (usedIds == null || usedIds.isEmpty()) {
            return countMaxScoreAnswers(questionId);
        }
        return countMaxScoreAnswersExcluding(questionId, usedIds);
    }

    /** Dispatches to the correct checkout query based on whether usedIds is empty. */
    default long countRemainingCheckouts(UUID questionId, int minScore, int maxScore, List<UUID> usedIds) {
        if (usedIds == null || usedIds.isEmpty()) {
            return countCheckoutAnswers(questionId, minScore, maxScore);
        }
        return countCheckoutAnswersExcluding(questionId, minScore, maxScore, usedIds);
    }

    /**
     * Returns all valid non-bust answer (id, score) pairs for a question.
     * Loaded once at game start to power in-memory hint computation — zero
     * database queries during gameplay.
     */
    @Query("""
        SELECT a.id, a.score FROM Answer a
        WHERE a.questionId = :questionId
          AND a.isValidDarts = true
          AND a.isBust = false
        """)
    List<Object[]> findValidNonBustAnswerScores(@Param("questionId") UUID questionId);

    /**
     * Returns true if the question has at least one valid non-bust answer
     * whose score is ≤ {@code maxScore}. Used to gate first-move viability:
     * if every answer exceeds the starting score, the player auto-busts on turn 1.
     */
    @Query("""
        SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Answer a
        WHERE a.questionId = :questionId
          AND a.isValidDarts = true
          AND a.isBust = false
          AND a.score <= :maxScore
        """)
    boolean hasViableFirstMove(
        @Param("questionId") UUID questionId,
        @Param("maxScore") int maxScore
    );

    /**
     * Returns true if the question has at least one valid non-bust, unused answer
     * whose score is playable from the current position (won't bust below -10).
     * Used to detect bust-out: if no valid move remains, the game is over.
     *
     * @param questionId    the question UUID
     * @param maxScore      the highest acceptable answer score (currentScore + 10)
     * @param usedAnswerIds already-used answer IDs to exclude (empty list = none)
     */
    @Query("""
        SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Answer a
        WHERE a.questionId = :questionId
          AND a.isValidDarts = true
          AND a.isBust = false
          AND a.score <= :maxScore
          AND (:usedAnswerIds IS NULL OR a.id NOT IN :usedAnswerIds)
        """)
    boolean hasViableMove(
        @Param("questionId") UUID questionId,
        @Param("maxScore") int maxScore,
        @Param("usedAnswerIds") List<UUID> usedAnswerIds
    );
}
