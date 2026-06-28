package com.trivia501.service;

import com.trivia501.dto.FootballFilter;
import com.trivia501.model.Category;
import com.trivia501.model.Question;
import com.trivia501.repository.AnswerRepository;
import com.trivia501.repository.CategoryRepository;
import com.trivia501.repository.QuestionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing questions and question selection.
 *
 * Responsibilities:
 * - Select random questions for games
 * - Ensure questions have sufficient answers
 * - Manage categories
 */
@Service
@Slf4j
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final CategoryRepository categoryRepository;
    private final AnswerRepository answerRepository;

    static final int DEFAULT_MIN_ANSWERS = 10;

    public QuestionService(
        QuestionRepository questionRepository,
        CategoryRepository categoryRepository,
        AnswerRepository answerRepository
    ) {
        this.questionRepository = questionRepository;
        this.categoryRepository = categoryRepository;
        this.answerRepository = answerRepository;
    }

    /**
     * Select a random active question for a category.
     * Uses default minimum answer count.
     */
    @Transactional(readOnly = true)
    public Optional<Question> selectRandomQuestion(UUID categoryId) {
        return selectRandomQuestion(categoryId, null, DEFAULT_MIN_ANSWERS);
    }

    /**
     * Select a random active question for a category with a custom minimum answer count.
     *
     * @param categoryId the category UUID
     * @param minAnswers minimum number of answers required
     * @return optional question (empty if none available)
     */
    @Transactional(readOnly = true)
    public Optional<Question> selectRandomQuestion(UUID categoryId, int minAnswers) {
        return selectRandomQuestion(categoryId, null, minAnswers);
    }

    /**
     * Select a random active question for a category with difficulty and minimum answer requirement.
     *
     * <p>Uses {@link QuestionRepository#findRandomActiveQuestion} which filters on the
     * denormalized {@code total_valid_count} column (no correlated subquery) and returns a
     * single row via {@code ORDER BY RANDOM() LIMIT 1}.
     */
    @Transactional(readOnly = true)
    public Optional<Question> selectRandomQuestion(UUID categoryId, Integer difficulty, int minAnswers) {
        log.debug("Selecting random question for category {} with difficulty {} and minAnswers {}",
            categoryId, difficulty, minAnswers);

        Optional<Question> selected = questionRepository.findRandomActiveQuestion(categoryId, difficulty, minAnswers);

        if (selected.isEmpty()) {
            log.warn("No eligible questions for category {} (difficulty: {}, minAnswers: {})",
                categoryId, difficulty, minAnswers);
        } else {
            log.debug("Selected question: {} (ID: {})", selected.get().getQuestionText(), selected.get().getId());
        }

        return selected;
    }

    /**
     * Get question by ID.
     *
     * @param questionId the question UUID
     * @return optional question
     */
    @Transactional(readOnly = true)
    public Optional<Question> getQuestionById(UUID questionId) {
        return questionRepository.findById(questionId);
    }

    @Transactional(readOnly = true)
    public List<Question> getQuestionsByIds(List<UUID> questionIds) {
        return questionRepository.findAllById(questionIds);
    }

    /**
     * Check if a question has minimum number of answers.
     *
     * @param questionId the question UUID
     * @param minAnswers minimum required answers
     * @return true if question has sufficient answers
     */
    @Transactional(readOnly = true)
    public boolean hasMinimumAnswers(UUID questionId, int minAnswers) {
        long answerCount = answerRepository.countByQuestionId(questionId);
        return answerCount >= minAnswers;
    }

    /**
     * Get category by slug.
     *
     * @param slug the category slug (e.g., "football")
     * @return optional category
     */
    @Transactional(readOnly = true)
    public Optional<Category> getCategoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug);
    }

    /**
     * Get all active questions for a category.
     *
     * @param categoryId the category UUID
     * @return list of active questions
     */
    @Transactional(readOnly = true)
    public List<Question> getActiveQuestions(UUID categoryId) {
        return questionRepository.findActiveByCategoryId(categoryId); // filters status = 'active'
    }

    /**
     * Get all categories.
     *
     * @return list of all categories
     */
    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    /**
     * Get total count of answers for a question.
     *
     * @param questionId the question UUID
     * @return answer count
     */
    @Transactional(readOnly = true)
    public long getAnswerCount(UUID questionId) {
        return answerRepository.countByQuestionId(questionId);
    }

    /**
     * Select a question using a structured {@link FootballFilter}.
     *
     * <p>Lookup strategy per scope:
     * <ul>
     *   <li>{@code club}              — exact match on league + club + statType (statType null = random stat)</li>
     *   <li>{@code league}            — exact match on league + statType (statType null = random stat)</li>
     *   <li>{@code random_club_level} — random club question; league narrows to one league if supplied</li>
     *   <li>{@code random_league_level} — random league-scope question from any league</li>
     *   <li>{@code random_any}        — random from all football template questions</li>
     * </ul>
     *
     * @param filter the football question filter
     * @return matching question, or empty if none is available yet
     */
    @Transactional(readOnly = true)
    public Optional<Question> selectQuestionByFilter(FootballFilter filter) {
        String scope    = filter.getScope();
        String league   = filter.getLeague();
        String club     = filter.getClub();
        String statType = filter.getStatType();

        log.debug("Football filter lookup: scope={}, league={}, club={}, stat={}", scope, league, club, statType);

        Optional<Question> result = switch (scope) {
            case "club" -> {
                if (statType != null) {
                    yield questionRepository.findFootballClubQuestion(league, club, statType);
                } else {
                    yield questionRepository.findRandomFootballClubQuestion(league, club);
                }
            }
            case "league" -> {
                if (statType != null) {
                    Optional<Question> exact = questionRepository.findFootballLeagueQuestion(league, statType);
                    // Fall back to any club question in that league — league-scope questions don't exist yet
                    yield exact.isPresent() ? exact
                        : league != null ? questionRepository.findRandomFootballClubInLeague(league)
                        : questionRepository.findRandomFootballAnyQuestion();
                } else {
                    yield league != null
                        ? questionRepository.findRandomFootballLeagueQuestionInLeague(league)
                            .or(() -> questionRepository.findRandomFootballClubInLeague(league))
                        : questionRepository.findRandomFootballAnyQuestion();
                }
            }
            case "random_club_level" -> league != null
                ? questionRepository.findRandomFootballClubInLeague(league)
                : questionRepository.findRandomFootballAnyQuestion();
            case "random_league_level" -> {
                // League-scope questions (q_scope='league') don't exist yet; fall back to any football question
                Optional<Question> leagueQ = questionRepository.findRandomFootballLeagueQuestion();
                yield leagueQ.isPresent() ? leagueQ : questionRepository.findRandomFootballAnyQuestion();
            }
            default -> questionRepository.findRandomFootballAnyQuestion(); // random_any + fallback
        };

        if (result.isEmpty()) {
            log.warn("No football question matched filter: scope={}, league={}, club={}, stat={}",
                scope, league, club, statType);
        }
        return result;
    }

    /**
     * Return {id (slug), name (display)} pairs for clubs with active questions in a league.
     * Used by the frontend's "Choose Your Board" club picker.
     */
    @Transactional(readOnly = true)
    public List<Map<String, String>> getClubsForLeague(String leagueSlug) {
        return questionRepository.findDistinctClubsByLeague(leagueSlug)
            .stream()
            .map(row -> Map.of("id", (String) row[0], "name", (String) row[1]))
            .toList();
    }
}
