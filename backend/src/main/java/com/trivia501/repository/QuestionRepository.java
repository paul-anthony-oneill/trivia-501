package com.trivia501.repository;

import com.trivia501.model.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@link Question} entity.
 *
 * <p>All "active question" queries filter on {@code status = 'active'}.
 * Use {@link Question#STATUS_ACTIVE} rather than hard-coding the string in callers.
 */
@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {

    // ── Simple status/category lookups ────────────────────────────────────────

    List<Question> findByStatus(String status);

    List<Question> findByCategoryIdAndStatus(UUID categoryId, String status);

    List<Question> findByCategoryIdAndDifficultyAndStatus(UUID categoryId, Integer difficulty, String status);

    /**
     * Convenience alias — returns all questions with {@code status = 'active'}
     * for the given category.
     */
    default List<Question> findActiveByCategoryId(UUID categoryId) {
        return findByCategoryIdAndStatus(categoryId, Question.STATUS_ACTIVE);
    }

    // ── Paged lookups ─────────────────────────────────────────────────────────

    Page<Question> findByCategoryId(UUID categoryId, Pageable pageable);

    Page<Question> findByCategoryIdAndStatus(UUID categoryId, String status, Pageable pageable);

    Page<Question> findByStatus(String status, Pageable pageable);

    // ── Answer-count-aware lookups (avoids N+1) ───────────────────────────────

    /**
     * Returns active questions for a category that have at least {@code minAnswers}
     * pre-materialised answers. Uses a single correlated subquery to avoid N+1.
     */
    @Query("""
        SELECT q FROM Question q
        WHERE q.categoryId = :categoryId
          AND q.status     = 'active'
          AND (SELECT COUNT(a) FROM Answer a WHERE a.questionId = q.id) >= :minAnswers
        """)
    List<Question> findActiveWithMinAnswers(
        @Param("categoryId") UUID categoryId,
        @Param("minAnswers") int minAnswers
    );

    /**
     * Same as {@link #findActiveWithMinAnswers} with an additional difficulty filter.
     */
    @Query("""
        SELECT q FROM Question q
        WHERE q.categoryId = :categoryId
          AND q.status     = 'active'
          AND q.difficulty = :difficulty
          AND (SELECT COUNT(a) FROM Answer a WHERE a.questionId = q.id) >= :minAnswers
        """)
    List<Question> findActiveWithMinAnswersByDifficulty(
        @Param("categoryId") UUID categoryId,
        @Param("difficulty") Integer difficulty,
        @Param("minAnswers") int minAnswers
    );

    /**
     * Returns a single random active question for a category with at least
     * {@code minAnswers} valid-darts answers, optionally filtered by difficulty.
     *
     * <p>Uses the denormalized {@code total_valid_count} column (V13) instead of a
     * correlated subquery against {@code answers} — a simple column comparison that
     * does not touch the answers table at all.
     *
     * @param categoryId  category UUID
     * @param difficulty  optional difficulty tier filter (pass {@code null} for all)
     * @param minAnswers  minimum number of valid-darts answers required
     * @return a random matching question, or empty if none qualify
     */
    @Query("""
        SELECT q FROM Question q
        WHERE q.categoryId = :categoryId
          AND q.status = 'active'
          AND q.totalValidCount >= :minAnswers
          AND (:difficulty IS NULL OR q.difficulty = :difficulty)
        ORDER BY function('random')
        LIMIT 1
        """)
    Optional<Question> findRandomActiveQuestion(
        @Param("categoryId") UUID categoryId,
        @Param("difficulty") Integer difficulty,
        @Param("minAnswers") int minAnswers
    );

    // ── Template-generator duplicate check ───────────────────────────────────

    /**
     * Returns {@code true} if a non-retired question exists for this template
     * with matching params (to prevent the generator creating duplicates).
     *
     * <p>Uses a native JSONB equality check because Spring Data cannot
     * auto-derive a {@code Map}-equality predicate from method names.
     *
     * <p>Pass {@code paramsJson} as a pre-serialised JSON string; the query casts
     * it to {@code jsonb} server-side.  Using {@code CAST} instead of the
     * {@code ::jsonb} shorthand avoids a SpEL-expression parsing quirk in
     * Spring Data's native-query parameter binder.
     */
    @Query(value = """
        SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
          FROM questions
         WHERE template_id     = :templateId
           AND template_params = CAST(:paramsJson AS jsonb)
           AND status         != :status
        """, nativeQuery = true)
    boolean existsByTemplateIdAndTemplateParamsAndStatusNot(
        @Param("templateId")  UUID   templateId,
        @Param("paramsJson")  String paramsJson,
        @Param("status")      String status
    );

    // ── Difficulty & viability queries ───────────────────────────────────────

    /**
     * Returns all questions where {@code difficulty_locked = false}.
     * Used by {@code DifficultyRecalibrationService} to find questions whose
     * scores should be recomputed.
     */
    List<Question> findByDifficultyLockedFalse();

    /**
     * Standard game selection query for a category and difficulty range.
     *
     * <p>Only returns questions that are {@code status = 'active'},
     * {@code single_question_viable = true}, and whose {@code difficulty_score}
     * falls within [{@code minScore}, {@code maxScore}].
     *
     * <p>For standard ranked play, pass {@code maxScore = 8.5} to exclude
     * questions that are playable but produce frustratingly constrained games.
     * See {@code DifficultyConstants} for the rationale.
     *
     * <p>Results are ordered randomly so each draw produces a fresh question.
     *
     * @param categoryId category UUID
     * @param minScore   minimum difficulty score (inclusive)
     * @param maxScore   maximum difficulty score (inclusive); use 8.5 for standard ranked play
     * @return matching questions in random order
     */
    @Query(value = """
        SELECT * FROM questions
        WHERE category_id             = :categoryId
          AND status                  = 'active'
          AND single_question_viable  = true
          AND difficulty_score        BETWEEN :minScore AND :maxScore
        ORDER BY RANDOM()
        """, nativeQuery = true)
    List<Question> findViableByDifficultyRange(
        @Param("categoryId") UUID   categoryId,
        @Param("minScore")   double minScore,
        @Param("maxScore")   double maxScore
    );

    /**
     * Returns all questions with {@code checkout_count = 0} and the given status.
     * Used in admin diagnostics to identify questions with no precision answers.
     */
    List<Question> findByCheckoutCountAndStatus(int checkoutCount, String status);

    // ── Daily Challenge selection ──────────────────────────────────────────────

    /**
     * Select a random question suitable for a Daily Challenge.
     *
     * <p>Filters on:
     * <ul>
     *   <li>{@code suitable_for_daily = true} — admin-curated pool</li>
     *   <li>{@code status = 'active'}</li>
     *   <li>{@code total_score_pool >= :startingScore} — question must have enough
     *       total answer points to support the chosen starting score</li>
     * </ul>
     *
     * @param categoryId    category UUID
     * @param startingScore the target score for the challenge
     * @param excludeIds    question IDs to exclude (e.g. used in the last 10 days);
     *                      pass a list containing only {@code 00000000-0000-0000-0000-000000000000}
     *                      when there is nothing to exclude
     * @return a random matching question, or empty if none qualify
     */
    @Query(value = """
        SELECT q.* FROM questions q
        WHERE q.category_id             = :categoryId
          AND q.status                  = 'active'
          AND q.suitable_for_daily      = true
          AND q.total_score_pool       >= :startingScore
          AND q.id                     NOT IN (:excludeIds)
        ORDER BY RANDOM()
        LIMIT 1
        """, nativeQuery = true)
    Optional<Question> findRandomDailyQuestion(
        @Param("categoryId")    UUID        categoryId,
        @Param("startingScore") int         startingScore,
        @Param("excludeIds")    List<UUID>  excludeIds
    );

    // ── Football template lookups (V24 columns) ───────────────────────────────

    /** Exact club-scope question lookup. Returns the one canonical question for this combination. */
    @Query("""
        SELECT q FROM Question q
        WHERE q.qScope  = 'club'
          AND q.qLeague = :league
          AND q.qClub   = :club
          AND q.qStat   = :statType
          AND q.status  = 'active'
        """)
    Optional<Question> findFootballClubQuestion(
        @Param("league")   String league,
        @Param("club")     String club,
        @Param("statType") String statType
    );

    /** Exact league-scope question lookup (no club). */
    @Query("""
        SELECT q FROM Question q
        WHERE q.qScope  = 'league'
          AND q.qLeague = :league
          AND q.qStat   = :statType
          AND q.status  = 'active'
        """)
    Optional<Question> findFootballLeagueQuestion(
        @Param("league")   String league,
        @Param("statType") String statType
    );

    /** Random club-scope question for a specific club (any stat type). */
    @Query("""
        SELECT q FROM Question q
        WHERE q.qScope  = 'club'
          AND q.qLeague = :league
          AND q.qClub   = :club
          AND q.status  = 'active'
        ORDER BY function('random')
        LIMIT 1
        """)
    Optional<Question> findRandomFootballClubQuestion(
        @Param("league") String league,
        @Param("club")   String club
    );

    /** Random club-scope question from any club in a given league. */
    @Query("""
        SELECT q FROM Question q
        WHERE q.qScope  = 'club'
          AND q.qLeague = :league
          AND q.status  = 'active'
        ORDER BY function('random')
        LIMIT 1
        """)
    Optional<Question> findRandomFootballClubInLeague(@Param("league") String league);

    /** Random league-scope question for a specific league (any stat). */
    @Query("""
        SELECT q FROM Question q
        WHERE q.qScope  = 'league'
          AND q.qLeague = :league
          AND q.status  = 'active'
        ORDER BY function('random')
        LIMIT 1
        """)
    Optional<Question> findRandomFootballLeagueQuestionInLeague(@Param("league") String league);

    /** Random league-scope question (any league, any stat). */
    @Query("""
        SELECT q FROM Question q
        WHERE q.qScope = 'league'
          AND q.status = 'active'
        ORDER BY function('random')
        LIMIT 1
        """)
    Optional<Question> findRandomFootballLeagueQuestion();

    /** Random from all football template questions (club or league scope, any stat). */
    @Query("""
        SELECT q FROM Question q
        WHERE q.qScope IS NOT NULL
          AND q.status = 'active'
        ORDER BY function('random')
        LIMIT 1
        """)
    Optional<Question> findRandomFootballAnyQuestion();

    /** Returns distinct club slugs that have at least one active question for the given league. */
    @Query(value = """
        SELECT DISTINCT q_club FROM questions
        WHERE q_scope  = 'club'
          AND q_league = :league
          AND status   = 'active'
          AND q_club   IS NOT NULL
        ORDER BY q_club
        """, nativeQuery = true)
    List<String> findDistinctClubsByLeague(@Param("league") String league);

    // ── Counts ────────────────────────────────────────────────────────────────

    boolean existsByCategoryIdAndSuitableForDailyTrueAndStatus(UUID categoryId, String status);

    long countByCategoryId(UUID categoryId);

    /**
     * Count questions generated from a specific template, filtered by lifecycle
     * status.  Used by the admin template list to show draft/active counts per
     * template at a glance.
     *
     * @param templateId the template UUID
     * @param status     lifecycle status string (e.g. {@code "draft"}, {@code "active"})
     */
    long countByTemplateIdAndStatus(UUID templateId, String status);

    org.springframework.data.domain.Page<Question> findByTemplateIdAndStatus(
        UUID templateId, String status, org.springframework.data.domain.Pageable pageable);
}
