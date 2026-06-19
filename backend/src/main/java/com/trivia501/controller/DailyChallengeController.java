package com.trivia501.controller;

import com.trivia501.dto.AnswerDebugResponse;
import com.trivia501.dto.DailyChallengeShareResponse;
import com.trivia501.dto.DailyChallengeStatusResponse;
import com.trivia501.dto.GameHints;
import com.trivia501.dto.GameStateResponse;
import com.trivia501.dto.MoveDto;
import com.trivia501.dto.SubmitAnswerRequest;
import com.trivia501.dto.SubmitAnswerResponse;
import com.trivia501.model.*;
import com.trivia501.repository.AnswerRepository;
import com.trivia501.repository.CategoryRepository;
import com.trivia501.scheduler.DailyChallengeScheduler;
import com.trivia501.service.DailyChallengeService;
import com.trivia501.service.GameHintsService;
import com.trivia501.service.GameService;
import com.trivia501.service.MatchService;
import com.trivia501.service.QuestionService;
import com.trivia501.security.OptionalJwtFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/daily-challenge")
@Slf4j
public class DailyChallengeController {

    private final DailyChallengeService dailyChallengeService;
    private final DailyChallengeScheduler dailyChallengeScheduler;
    private final GameService gameService;
    private final MatchService matchService;
    private final QuestionService questionService;
    private final GameHintsService gameHintsService;
    private final CategoryRepository categoryRepository;
    private final com.trivia501.service.PlayerProfileService playerProfileService;
    private final AnswerRepository answerRepository;

    public DailyChallengeController(
            DailyChallengeService dailyChallengeService,
            DailyChallengeScheduler dailyChallengeScheduler,
            GameService gameService,
            MatchService matchService,
            QuestionService questionService,
            GameHintsService gameHintsService,
            CategoryRepository categoryRepository,
            com.trivia501.service.PlayerProfileService playerProfileService,
            AnswerRepository answerRepository
    ) {
        this.dailyChallengeService = dailyChallengeService;
        this.dailyChallengeScheduler = dailyChallengeScheduler;
        this.gameService = gameService;
        this.matchService = matchService;
        this.questionService = questionService;
        this.gameHintsService = gameHintsService;
        this.categoryRepository = categoryRepository;
        this.playerProfileService = playerProfileService;
        this.answerRepository = answerRepository;
    }

    /**
     * Returns today's challenge status for all categories that have a challenge.
     */
    @GetMapping("/status")
    public ResponseEntity<DailyChallengeStatusResponse> getStatus() {
        List<DailyChallenge> challenges = dailyChallengeService.getTodaysChallenges();

        // Bulk-fetch categories and questions to avoid N+1 queries
        List<UUID> categoryIds = challenges.stream()
                .map(DailyChallenge::getCategoryId).distinct().toList();
        List<UUID> questionIds = challenges.stream()
                .map(DailyChallenge::getQuestionId).distinct().toList();

        Map<UUID, Category> categoriesById = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, c -> c));
        Map<UUID, Question> questionsById = questionService.getQuestionsByIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        List<DailyChallengeStatusResponse.CategoryChallenge> items = new ArrayList<>();
        for (DailyChallenge dc : challenges) {
            Category category = categoriesById.get(dc.getCategoryId());
            Question question = questionsById.get(dc.getQuestionId());

            items.add(DailyChallengeStatusResponse.CategoryChallenge.builder()
                    .categorySlug(category != null ? category.getSlug() : "unknown")
                    .categoryName(category != null ? category.getName() : "Unknown")
                    .startingScore(dc.getStartingScore())
                    .questionText(question != null ? question.getQuestionText() : null)
                    .hasChallenge(true)
                    .build());
        }

        return ResponseEntity.ok(DailyChallengeStatusResponse.builder()
                .date(java.time.LocalDate.now())
                .challenges(items)
                .build());
    }

    /**
     * Generates today's daily challenge for all categories.
     * Pass ?force=true to delete any existing challenge and regenerate.
     */
    @PostMapping("/admin/generate-today")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.Map<String, Object>> generateToday(
            @RequestParam(defaultValue = "false") boolean force) {
        var cats = categoryRepository.findAll();
        int created = 0;
        int alreadyExisted = 0;
        int failed = 0;
        var today = java.time.LocalDate.now();
        for (var cat : cats) {
            if ("test".equals(cat.getSlug())) continue;
            try {
                boolean existed = dailyChallengeService.todaysChallengeExists(cat.getId());
                if (force) {
                    dailyChallengeService.deleteTodaysChallenge(cat.getId());
                    existed = false;
                }
                if (existed) {
                    alreadyExisted++;
                } else {
                    dailyChallengeService.getTodaysChallenge(cat.getId());
                    created++;
                }
            } catch (IllegalStateException e) {
                log.warn("No viable question for category '{}' today — {}", cat.getSlug(), e.getMessage());
                failed++;
            }
        }
        return ResponseEntity.ok(java.util.Map.of(
                "status", "generation complete",
                "created", created,
                "alreadyExisted", alreadyExisted,
                "failed", failed
        ));
    }

    /**
     * Triggers monthly daily challenge generation for all categories.
     * Idempotent — skips days that already have a challenge.
     */
    @PostMapping("/admin/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.Map<String, Object>> generateChallenges() {
        var summary = dailyChallengeScheduler.selectDailyChallenges();
        return ResponseEntity.ok(java.util.Map.of(
                "status", "generation complete",
                "created", summary.created(),
                "skipped", summary.skipped(),
                "failed", summary.failed()
        ));
    }

    /**
     * Returns status for a single category's daily challenge.
     */
    @GetMapping("/{categorySlug}")
    public ResponseEntity<DailyChallengeStatusResponse.CategoryChallenge> getCategoryStatus(
            @PathVariable String categorySlug
    ) {
        try {
            DailyChallenge dc = dailyChallengeService.getTodaysChallenge(categorySlug);
            Category category = categoryRepository.findById(dc.getCategoryId()).orElse(null);
            Question question = questionService.getQuestionById(dc.getQuestionId()).orElse(null);

            return ResponseEntity.ok(DailyChallengeStatusResponse.CategoryChallenge.builder()
                    .categorySlug(categorySlug)
                    .categoryName(category != null ? category.getName() : "Unknown")
                    .startingScore(dc.getStartingScore())
                    .questionText(question != null ? question.getQuestionText() : null)
                    .hasChallenge(true)
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Start a daily challenge game for the given category.
     */
    @PostMapping("/{categorySlug}/start")
    public ResponseEntity<GameStateResponse> startDailyChallenge(
            @PathVariable String categorySlug,
            Principal principal
    ) {
        UUID playerId = playerIdFrom(principal);
        playerProfileService.ensureProfile(playerId);

        log.debug("Starting daily challenge for player {} in category '{}'", playerId, categorySlug);

        DailyChallengeService.GameStartRecord startRecord =
                dailyChallengeService.startDailyChallenge(playerId, categorySlug);

        Game game = startRecord.game();
        Question question = startRecord.question();

        gameHintsService.loadScoreCache(question.getId());

        log.info("Daily challenge game started: gameId={}, playerId={}, category={}, startingScore={}",
                game.getId(), playerId, categorySlug, startRecord.challenge().getStartingScore());

        return ResponseEntity.ok(buildGameStateResponse(game, question, startRecord.match(), List.of(), List.of()));
    }

    /**
     * Submit an answer for the current daily challenge game.
     */
    @PostMapping("/games/{gameId}/submit")
    public ResponseEntity<SubmitAnswerResponse> submitAnswer(
            @PathVariable UUID gameId,
            @Valid @RequestBody SubmitAnswerRequest request,
            Principal principal,
            HttpServletRequest httpRequest
    ) {
        UUID playerId = playerIdFrom(principal);
        log.debug("Submitting daily challenge answer for game {}: '{}'", gameId, request.getAnswer());

        GameService.MoveRecord result = gameService.processPlayerMove(
                gameId, playerId, request.getAnswer(), request.getEntityId());

        Game game = result.game();
        Match match = result.match();

        Question question = questionService.getQuestionById(game.getQuestionId())
                .orElseThrow(() -> new IllegalStateException("Question not found"));

        GameMove move = result.move();

        // Rotate anonymous session cookie on game completion to limit exfiltration window
        if (move.getResult() == GameMove.MoveResult.CHECKOUT
                && OptionalJwtFilter.AUTH_TYPE_ANON.equals(httpRequest.getAttribute(OptionalJwtFilter.AUTH_TYPE_ATTR))) {
            httpRequest.setAttribute(OptionalJwtFilter.ROTATE_ANON_ATTR, "true");
        }

        SubmitAnswerResponse response = SubmitAnswerResponse.builder()
                .result(move.getResult().name())
                .matchedAnswer(move.getMatchedDisplayText())
                .scoreValue(move.getScoreValue())
                .scoreBefore(move.getScoreBefore())
                .scoreAfter(move.getScoreAfter())
                .reason(result.reason())
                .isWin(move.getResult() == GameMove.MoveResult.CHECKOUT)
                .gameState(buildGameStateResponse(game, question, match, result.usedAnswerIds(), List.of()))
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Abandon an in-progress daily challenge game.
     */
    @PostMapping("/games/{gameId}/abandon")
    public ResponseEntity<Void> abandonGame(
            @PathVariable UUID gameId,
            Principal principal
    ) {
        UUID playerId = playerIdFrom(principal);
        log.debug("Abandoning daily challenge game {} for player {}", gameId, playerId);
        gameService.abandonGame(gameId, playerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get current daily challenge game state including move history.
     */
    @GetMapping("/games/{gameId}")
    public ResponseEntity<GameStateResponse> getGameState(
            @PathVariable UUID gameId,
            Principal principal
    ) {
        UUID playerId = playerIdFrom(principal);
        log.debug("Getting daily challenge game state for game {} (requestedBy={})", gameId, playerId);

        Game game = gameService.getGameById(gameId).orElse(null);
        if (game == null) {
            return ResponseEntity.notFound().build();
        }

        Match match = matchService.getMatchById(game.getMatchId())
                .orElseThrow(() -> new IllegalStateException("Match not found"));

        Question question = questionService.getQuestionById(game.getQuestionId())
                .orElseThrow(() -> new IllegalStateException("Question not found"));

        List<GameMove> moves = gameService.getMovesForGame(gameId);

        return ResponseEntity.ok(buildGameStateResponse(game, question, match, moves));
    }

    /**
     * Get share data for a completed daily challenge game.
     */
    @GetMapping("/share/{gameId}")
    public ResponseEntity<DailyChallengeShareResponse> getShareData(@PathVariable UUID gameId) {
        Game game = gameService.getGameById(gameId).orElse(null);
        if (game == null) {
            return ResponseEntity.notFound().build();
        }

        List<GameMove> moves = gameService.getMovesForGame(gameId);

        // Match the game to today's daily challenge for the same question
        DailyChallenge challenge = dailyChallengeService.getTodaysChallenges().stream()
                .filter(dc -> dc.getQuestionId().equals(game.getQuestionId()))
                .findFirst()
                .orElse(null);

        String categoryName = "Unknown";
        String categorySlug = "unknown";
        int startingScore = 501;
        java.time.LocalDate challengeDate = java.time.LocalDate.now();

        if (challenge != null) {
            startingScore = challenge.getStartingScore();
            challengeDate = challenge.getChallengeDate();
            Category category = categoryRepository.findById(challenge.getCategoryId()).orElse(null);
            if (category != null) {
                categoryName = category.getName();
                categorySlug = category.getSlug();
            }
        }

        List<DailyChallengeShareResponse.MoveEmoji> emojis = moves.stream()
                .map(m -> switch (m.getResult()) {
                    case VALID -> DailyChallengeShareResponse.MoveEmoji.VALID;
                    case BUST -> DailyChallengeShareResponse.MoveEmoji.BUST;
                    case INVALID -> DailyChallengeShareResponse.MoveEmoji.INVALID;
                    case CHECKOUT -> DailyChallengeShareResponse.MoveEmoji.CHECKOUT;
                    case TIMEOUT -> DailyChallengeShareResponse.MoveEmoji.INVALID;
                })
                .toList();

        boolean isWin = game.getWinnerId() != null;

        return ResponseEntity.ok(DailyChallengeShareResponse.builder()
                .gameId(gameId)
                .categoryName(categoryName)
                .categorySlug(categorySlug)
                .challengeDate(challengeDate)
                .startingScore(startingScore)
                .finalScore(game.getPlayer1Score())
                .turnCount(game.getTurnCount())
                .isWin(isWin)
                .moveEmojis(emojis)
                .resultToken(game.getResultToken())
                .build());
    }

    /**
     * Debug endpoint: returns all answers for the game's question.
     * Restricted to admins only — this is the answer key.
     */
    @GetMapping("/games/{gameId}/answers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AnswerDebugResponse>> getGameAnswers(
        @PathVariable UUID gameId,
        Principal principal
    ) {
        UUID playerId = playerIdFrom(principal);
        Game game = gameService.getGameById(gameId).orElse(null);
        if (game == null) return ResponseEntity.notFound().build();

        return ResponseEntity.ok(
            answerRepository.findByQuestionIdOrderByScoreDesc(game.getQuestionId())
                .stream()
                .map(a -> new AnswerDebugResponse(
                    a.getId(), a.getDisplayText(), a.getScore(),
                    a.getIsValidDarts(), a.getIsBust()))
                .toList()
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private UUID playerIdFrom(Principal principal) {
        return UUID.fromString(principal.getName());
    }

    private GameStateResponse buildGameStateResponse(Game game, Question question, Match match,
                                                      List<GameMove> moves) {
        return buildGameStateResponse(game, question, match, null, moves);
    }

    private GameStateResponse buildGameStateResponse(Game game, Question question, Match match,
                                                      List<UUID> usedAnswerIds, List<GameMove> moves) {
        int currentScore = game.getPlayer1Score();

        boolean isWin = game.getStatus() == Game.GameStatus.COMPLETED
                && game.getWinnerId() != null
                && game.getWinnerId().equals(match.getPlayer1Id());

        String entityType = EntityType.FOOTBALLER;
        if (question.getConfig() != null) {
            Object configEntityType = question.getConfig().get("entity_type");
            if (configEntityType instanceof String s && !s.isBlank()) {
                entityType = s;
            }
        }

        GameHints hints = usedAnswerIds != null
                ? gameHintsService.computeHintsFromCache(game.getQuestionId(), usedAnswerIds, currentScore)
                : gameHintsService.computeHints(game.getId(), game.getQuestionId(), currentScore);

        List<MoveDto> moveDtos = moves != null
                ? moves.stream().map(this::toMoveDto).toList()
                : null;

        return GameStateResponse.builder()
                .gameId(game.getId())
                .matchId(game.getMatchId())
                .questionId(game.getQuestionId())
                .questionText(question.getQuestionText())
                .currentScore(currentScore)
                .turnCount(game.getTurnCount())
                .status(game.getStatus().name())
                .isWin(isWin)
                .turnTimerSeconds(game.getTurnTimerSeconds())
                .entityType(entityType)
                .hints(hints)
                .moves(moveDtos)
                .build();
    }

    private MoveDto toMoveDto(GameMove move) {
        return new MoveDto(
                move.getSubmittedAnswer(),
                move.getResult().name(),
                move.getScoreBefore(),
                move.getScoreAfter(),
                move.getMatchedDisplayText(),
                move.getScoreValue()
        );
    }

}
