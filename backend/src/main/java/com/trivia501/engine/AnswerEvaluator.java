package com.trivia501.engine;

import com.trivia501.model.Answer;
import com.trivia501.model.NamedEntity;
import com.trivia501.repository.AnswerRepository;
import com.trivia501.repository.NamedEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for evaluating player answers during gameplay.
 *
 * Core responsibilities:
 * - Validate user input against pre-populated answers via exact answer-key match
 * - Apply darts scoring rules
 * - Determine win/bust conditions
 * - Track used answers to prevent reuse
 *
 * Fuzzy matching has been removed. The autocomplete dropdown (powered by the
 * {@code entities} table and its GIN trigram index) now handles accent
 * differences and typos. The player selects an entity from the dropdown, the
 * frontend sends its UUID, and this evaluator resolves it to a normalized
 * name for exact lookup against {@code answers.answer_key}.
 *
 * Critical: All validation uses pre-cached data from the answers table.
 * NO external API calls are made during gameplay.
 */
@Service
@Slf4j
public class AnswerEvaluator {

    private final AnswerRepository answerRepository;
    private final NamedEntityRepository namedEntityRepository;
    private final ScoringService scoringService;

    /**
     * Minimum similarity threshold for the fuzzy fallback (0.0 to 1.0).
     * Only used when the exact match misses — never on the hot path.
     */
    private static final double SIMILARITY_THRESHOLD = 0.5;

    public AnswerEvaluator(
        AnswerRepository answerRepository,
        NamedEntityRepository namedEntityRepository,
        ScoringService scoringService
    ) {
        this.answerRepository = answerRepository;
        this.namedEntityRepository = namedEntityRepository;
        this.scoringService = scoringService;
    }

    /**
     * Evaluate a player's answer during gameplay.
     *
     * @param questionId    the question UUID
     * @param userInput     the answer text as entered by the user (for display/move history)
     * @param entityId      the entity UUID from the autocomplete dropdown, or null if the
     *                      player typed a name without selecting a suggestion
     * @param currentScore  the player's current score (starts at 501)
     * @param usedAnswerIds list of already-used answer UUIDs
     * @return AnswerResult with validation details
     */
    @Transactional(readOnly = true)
    public AnswerResult evaluateAnswer(
        UUID questionId,
        String userInput,
        UUID entityId,
        int currentScore,
        List<UUID> usedAnswerIds
    ) {
        String answerKey = resolveAnswerKey(userInput, entityId);

        if (answerKey.isEmpty()) {
            return AnswerResult.invalid("Empty answer", currentScore);
        }

        Answer answer = findAnswer(questionId, answerKey);

        if (answer == null) {
            return AnswerResult.invalid("Answer not found", currentScore);
        }

        if (usedAnswerIds != null && usedAnswerIds.contains(answer.getId())) {
            return AnswerResult.invalid("Answer already used", currentScore);
        }

        // Calculate new score using ScoringService
        ScoreResult scoreResult = scoringService.calculateScore(currentScore, answer.getScore());

        boolean isBust = scoreResult.isBust() || answer.getIsBust();
        String reason = scoreResult.reason();

        return AnswerResult.valid(
            answer.getDisplayText(),
            answer.getId(),
            answer.getScore(),
            answer.getIsValidDarts(),
            isBust,
            scoreResult.newScore(),
            scoreResult.isCheckout(),
            reason,
            null
        );
    }

    /**
     * Find a matching answer using exact lookup, with a fuzzy fallback on miss.
     *
     * The fast path (entity-ID resolved, or exact text match) hits ~95%+ of
     * the time and costs a single B-tree index lookup. The fuzzy fallback
     * only fires when the player typed raw text without using autocomplete,
     * or on the rare case of data drift between {@code entities.normalized_name}
     * and {@code answers.answer_key}.
     *
     * <p>Used-answer exclusion is handled by the caller ({@link #evaluateAnswer})
     * so the correct "already used" message can be returned.
     *
     * <p>The fuzzy fallback is best-effort — if the database doesn't support
     * {@code pg_trgm} (e.g. H2 in tests), the error is caught and the method
     * returns null as if no match were found.
     */
    private Answer findAnswer(UUID questionId, String answerKey) {
        // Fast path: exact match on answer_key (B-tree index)
        Optional<Answer> exact = answerRepository.findByQuestionIdAndAnswerKey(questionId, answerKey);
        if (exact.isPresent()) {
            return exact.get();
        }

        // Accent-and-space-insensitive fallback: entity names are accent-stripped
        // in Java (keeping spaces) while answer_key from the Python scraper
        // strips spaces but preserves accents.  Normalise both sides to bare
        // alphanumeric before comparing so the two pipelines agree.
        Optional<Answer> normalised = answerRepository.findByQuestionIdAndAnswerKeyNormalised(
            questionId, answerKey);
        if (normalised.isPresent()) {
            return normalised.get();
        }

        // Last resort: fuzzy match via pg_trgm similarity (GIN trigram index).
        // Only reached when the key doesn't match at all — e.g. raw text with a
        // typo, or entities/answers drift. Best-effort: swallow if pg_trgm unavailable.
        try {
            Optional<Answer> fuzzy = answerRepository.findFuzzyMatch(
                questionId, answerKey, SIMILARITY_THRESHOLD);
            if (fuzzy.isPresent()) {
                log.debug("Fuzzy fallback matched '{}' to '{}'", answerKey, fuzzy.get().getDisplayText());
                return fuzzy.get();
            }
        } catch (Exception e) {
            log.debug("Fuzzy fallback unavailable (pg_trgm not installed?): {}", e.getMessage());
        }

        return null;
    }

    /**
     * Resolve the answer key to use for exact database lookup.
     *
     * When an entity UUID is provided (player selected from autocomplete),
     * the entity's pre-computed {@code normalizedName} is used — this is
     * guaranteed to match {@code answers.answer_key} because
     * {@code EntitySearchService.upsertEntity()} keeps them in sync.
     *
     * When no entity UUID is provided (player typed a raw name and pressed
     * Enter without selecting a suggestion), the raw text is lowercased and
     * trimmed for a best-effort exact match (with fuzzy fallback).
     */
    private String resolveAnswerKey(String userInput, UUID entityId) {
        if (entityId != null) {
            return namedEntityRepository.findById(entityId)
                .map(NamedEntity::getNormalizedName)
                .map(AnswerEvaluator::stripAccents)
                .orElseGet(() -> normalize(userInput));
        }
        return normalize(userInput);
    }

    private static String normalize(String input) {
        if (input == null) return "";
        return input.trim().toLowerCase();
    }

    /**
     * Strips combining diacritical marks from a normalized name so it matches
     * {@code answers.answer_key} after V18's {@code unaccent()} clean-up.
     * <p>
     * The {@code entities.normalized_name} column was backfilled from
     * {@code players.normalized_name}, which was written by the Python scraper
     * using {@code str.lower()} — this preserves accented characters.  V18 only
     * applied {@code unaccent()} to {@code answers.answer_key}, so the two
     * columns can diverge for names like Higuaín or Di María.
     */
    static String stripAccents(String input) {
        if (input == null) return "";
        return java.text.Normalizer
            .normalize(input, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}", "");
    }

    /**
     * Get count of remaining valid answers for a question.
     */
    @Transactional(readOnly = true)
    public long getAvailableAnswerCount(UUID questionId, List<UUID> usedAnswerIds) {
        return answerRepository.countAvailableAnswers(
            questionId,
            usedAnswerIds != null && !usedAnswerIds.isEmpty() ? usedAnswerIds : null
        );
    }

    /**
     * Get top scoring answers for a question.
     */
    @Transactional(readOnly = true)
    public List<Answer> getTopAnswers(
        UUID questionId,
        int limit,
        boolean excludeInvalidDarts
    ) {
        return answerRepository.findTopAnswers(
            questionId,
            excludeInvalidDarts,
            limit
        );
    }

    /**
     * Get statistics about a question's answers.
     */
    @Transactional(readOnly = true)
    public AnswerStats getAnswerStats(UUID questionId) {
        long totalAnswers = answerRepository.countByQuestionId(questionId);
        long validDartsAnswers = answerRepository.countByQuestionIdAndIsValidDartsTrue(questionId);
        return new AnswerStats(totalAnswers, validDartsAnswers);
    }

    /**
     * Simple record for answer statistics.
     */
    public record AnswerStats(long totalAnswers, long validDartsAnswers) {
        public long invalidOrBustAnswers() {
            return totalAnswers - validDartsAnswers;
        }
    }
}
