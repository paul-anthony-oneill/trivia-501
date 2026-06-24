package com.trivia501.service;

import com.trivia501.engine.ChallengeScorePicker;
import com.trivia501.engine.DifficultyConstants;
import com.trivia501.model.Category;
import com.trivia501.model.DailyChallenge;
import com.trivia501.model.Game;
import com.trivia501.model.Match;
import com.trivia501.model.Question;
import com.trivia501.repository.CategoryRepository;
import com.trivia501.repository.DailyChallengeRepository;
import com.trivia501.repository.GameRepository;
import com.trivia501.repository.MatchRepository;
import com.trivia501.repository.QuestionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class DailyChallengeService {

    private final DailyChallengeRepository challengeRepository;
    private final QuestionRepository questionRepository;
    private final CategoryRepository categoryRepository;
    private final MatchService matchService;
    private final GameService gameService;
    private final GameRepository gameRepository;
    private final MatchRepository matchRepository;
    private final ChallengeScorePicker scorePicker;

    public DailyChallengeService(
            DailyChallengeRepository challengeRepository,
            QuestionRepository questionRepository,
            CategoryRepository categoryRepository,
            MatchService matchService,
            GameService gameService,
            GameRepository gameRepository,
            MatchRepository matchRepository,
            ChallengeScorePicker scorePicker
    ) {
        this.challengeRepository = challengeRepository;
        this.questionRepository = questionRepository;
        this.categoryRepository = categoryRepository;
        this.matchService = matchService;
        this.gameService = gameService;
        this.gameRepository = gameRepository;
        this.matchRepository = matchRepository;
        this.scorePicker = scorePicker;
    }

    /**
     * Returns all of today's daily challenges across all active categories.
     */
    @Transactional(readOnly = true)
    public List<DailyChallenge> getTodaysChallenges() {
        return challengeRepository.findByChallengeDate(LocalDate.now());
    }

    /** Pass-through to {@code CategoryRepository.findAll()} so controllers don't inject repositories directly. */
    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    /** Pass-through to {@code CategoryRepository.findAllById()} so controllers don't inject repositories directly. */
    @Transactional(readOnly = true)
    public Map<UUID, Category> getCategoriesByIds(List<UUID> ids) {
        return categoryRepository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Category::getId, c -> c));
    }

    /** Pass-through to {@code CategoryRepository.findById()}. */
    @Transactional(readOnly = true)
    public Optional<Category> getCategoryById(UUID id) {
        return categoryRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<DailyChallenge> findByChallengeDateAndQuestionId(LocalDate date, UUID questionId) {
        return challengeRepository.findByChallengeDateAndQuestionId(date, questionId);
    }

    @Transactional(readOnly = true)
    public boolean todaysChallengeExists(UUID categoryId) {
        return challengeRepository
                .findByChallengeDateAndCategoryId(LocalDate.now(), categoryId)
                .isPresent();
    }

    /**
     * Deletes today's challenge for a category so it can be regenerated.
     * No-op if none exists.
     */
    @Transactional
    public void deleteTodaysChallenge(UUID categoryId) {
        LocalDate today = LocalDate.now();
        challengeRepository.findByChallengeDateAndCategoryId(today, categoryId)
                .ifPresent(challenge -> {
                    challengeRepository.delete(challenge);
                    log.info("Deleted daily challenge for {} / {}", today, categoryId);
                });
    }

    /**
     * Returns today's challenge for a specific category, creating it lazily if
     * the scheduler has not run yet (or if no question was pre-selected).
     */
    @Transactional
    public DailyChallenge getTodaysChallenge(UUID categoryId) {
        LocalDate today = LocalDate.now();
        return challengeRepository.findByChallengeDateAndCategoryId(today, categoryId)
                .orElseGet(() -> createChallenge(categoryId));
    }

    /**
     * Resolves a category slug to today's challenge.
     *
     * @throws IllegalArgumentException if the category slug is unknown
     */
    @Transactional
    public DailyChallenge getTodaysChallenge(String categorySlug) {
        Category category = categoryRepository.findBySlug(categorySlug)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categorySlug));
        return getTodaysChallenge(category.getId());
    }

    /**
     * Starts a daily challenge game for the given player and category.
     *
     * @param playerId     the authenticated player UUID
     * @param categorySlug the category slug (e.g. "football")
     * @return a result bundle with the created match, game, and question
     * @throws IllegalStateException if no viable question is available for today
     */
    @Transactional
    public GameStartRecord startDailyChallenge(UUID playerId, String categorySlug) {
        Category category = categoryRepository.findBySlug(categorySlug)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categorySlug));

        DailyChallenge challenge = getTodaysChallenge(category.getId());

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        // Block replay: a completed game today cannot be restarted
        gameRepository.findDailyGameByPlayerCategoryAndStatus(
                playerId, category.getId(), Match.MatchType.DAILY_CHALLENGE,
                Game.GameStatus.COMPLETED, startOfDay, endOfDay)
            .ifPresent(g -> {
                throw new IllegalStateException(
                    "You have already completed today's " + categorySlug + " daily challenge");
            });

        // Resume: return the existing in-progress game rather than creating a new one
        Optional<Game> inProgress = gameRepository.findDailyGameByPlayerCategoryAndStatus(
                playerId, category.getId(), Match.MatchType.DAILY_CHALLENGE,
                Game.GameStatus.IN_PROGRESS, startOfDay, endOfDay);
        if (inProgress.isPresent()) {
            Game existingGame = inProgress.get();
            Match existingMatch = matchRepository.findById(existingGame.getMatchId())
                    .orElseThrow(() -> new IllegalStateException("Match not found for existing daily game"));
            Question question = questionRepository.findById(challenge.getQuestionId())
                    .orElseThrow(() -> new IllegalStateException("Question not found for daily challenge"));
            log.info("Resuming daily challenge: gameId={}, playerId={}, category={}",
                    existingGame.getId(), playerId, categorySlug);
            return new GameStartRecord(existingMatch, existingGame, question, challenge);
        }

        // Fresh start: abandon any stale in-progress games from other modes or previous days
        gameService.abandonActiveGamesForPlayer(playerId);

        Match match = matchService.createMatch(
                playerId,
                category.getId(),
                Match.MatchType.DAILY_CHALLENGE,
                Match.MatchFormat.BEST_OF_1,
                null // difficulty — inferred from the selected question
        );
        match.setGameMode("DAILY_CHALLENGE");

        Game game = gameService.createGame(match.getId(), challenge.getQuestionId(), 1, challenge.getStartingScore());

        Question question = questionRepository.findById(challenge.getQuestionId())
                .orElseThrow(() -> new IllegalStateException("Question not found for daily challenge"));

        log.info("Daily challenge started: gameId={}, playerId={}, category={}, startingScore={}",
                game.getId(), playerId, categorySlug, challenge.getStartingScore());

        return new GameStartRecord(match, game, question, challenge);
    }

    /**
     * Picks a random starting score from the curated pool, optionally avoiding
     * the score used yesterday for the same category.
     */
    public int pickStartingScore(UUID categoryId) {
        return scorePicker.pickStartingScore(categoryId);
    }

    /**
     * Creates a daily challenge for the given category. Tries candidate scores
     * from the expanded pool, falling back if the first choice doesn't pair with
     * a viable question.
     *
     * <p>Each candidate question is verified for first-move viability — at least
     * one valid non-bust answer must be within the starting score + margin.
     */
    private DailyChallenge createChallenge(UUID categoryId) {
        // Defense-in-depth: the test category must never surface as a daily challenge.
        Category category = categoryRepository.findById(categoryId).orElse(null);
        if (category != null && "test".equals(category.getSlug())) {
            throw new IllegalArgumentException("No daily challenge for the test category");
        }

        LocalDate today = LocalDate.now();
        LocalDate cooldownStart = today.minusDays(DifficultyConstants.DAILY_QUESTION_COOLDOWN_DAYS);
        List<UUID> recentQuestionIds = challengeRepository.findQuestionIdsUsedBetween(
                categoryId, cooldownStart, today);
        int yesterdayScore = challengeRepository
            .findLatestStartingScoreBefore(categoryId, today)
            .orElse(-1);

        ChallengeScorePicker.QuestionScorePair result =
            scorePicker.findViableQuestionAndScore(
                categoryId, yesterdayScore, today, recentQuestionIds);

        if (result == null) {
            throw new IllegalStateException(
                "No suitable_for_daily question found for category " + categoryId +
                " with a playable first move. " +
                "Mark at least one question with suitable_for_daily = true.");
        }

        DailyChallenge challenge = DailyChallenge.builder()
                .challengeDate(today)
                .categoryId(categoryId)
                .questionId(result.question().getId())
                .startingScore(result.score())
                .status("active")
                .build();

        challenge = challengeRepository.save(challenge);
        log.info("Daily challenge created for {}: questionId={}, startingScore={}",
                today, result.question().getId(), result.score());
        return challenge;
    }

    /**
     * Result bundle for starting a daily challenge game.
     */
    public record GameStartRecord(Match match, Game game, Question question, DailyChallenge challenge) {}
}
