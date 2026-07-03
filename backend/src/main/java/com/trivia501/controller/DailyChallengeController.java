package com.trivia501.controller;

import com.trivia501.dto.AnswerDebugResponse;
import com.trivia501.dto.DailyChallengeShareResponse;
import com.trivia501.dto.DailyChallengeStatusResponse;
import com.trivia501.dto.GameStateResponse;
import com.trivia501.dto.SubmitAnswerRequest;
import com.trivia501.dto.SubmitAnswerResponse;
import com.trivia501.model.*;
import com.trivia501.scheduler.DailyChallengeScheduler;
import com.trivia501.service.DailyChallengeService;
import com.trivia501.service.GameService;
import com.trivia501.service.PlayerProfileService;
import com.trivia501.service.QuestionService;
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

@RestController
@RequestMapping("/api/daily-challenge")
@Slf4j
public class DailyChallengeController {

    private final DailyChallengeService dailyChallengeService;
    private final DailyChallengeScheduler dailyChallengeScheduler;
    private final QuestionService questionService;
    private final PlayerProfileService playerProfileService;
    private final GameService gameService;
    private final GameResponseAssembler assembler;
    private final GameEndpointHandler gameEndpointHandler;

    public DailyChallengeController(
            DailyChallengeService dailyChallengeService,
            DailyChallengeScheduler dailyChallengeScheduler,
            QuestionService questionService,
            PlayerProfileService playerProfileService,
            GameService gameService,
            GameResponseAssembler assembler,
            GameEndpointHandler gameEndpointHandler
    ) {
        this.dailyChallengeService = dailyChallengeService;
        this.dailyChallengeScheduler = dailyChallengeScheduler;
        this.questionService = questionService;
        this.playerProfileService = playerProfileService;
        this.gameService = gameService;
        this.assembler = assembler;
        this.gameEndpointHandler = gameEndpointHandler;
    }

    /**
     * Returns today's challenge status for all categories, lazily creating
     * any that don't exist yet. This ensures the homepage hero section is
     * never empty, even if the cron missed a day.
     */
    @GetMapping("/status")
    public ResponseEntity<DailyChallengeStatusResponse> getStatus() {
        List<Category> categories = dailyChallengeService.getAllCategories();
        List<DailyChallengeStatusResponse.CategoryChallenge> items = new ArrayList<>();

        for (Category category : categories) {
            if ("test".equals(category.getSlug())) continue;
            try {
                DailyChallenge dc = dailyChallengeService.getTodaysChallenge(category.getId());
                Question question = questionService.getQuestionById(dc.getQuestionId()).orElse(null);

                items.add(DailyChallengeStatusResponse.CategoryChallenge.builder()
                        .categorySlug(category.getSlug())
                        .categoryName(category.getName())
                        .startingScore(dc.getStartingScore())
                        .questionText(question != null ? question.getQuestionText() : null)
                        .hasChallenge(true)
                        .build());
            } catch (IllegalStateException e) {
                // Category has no daily-eligible questions — skip
                log.debug("No daily challenge for '{}': {}", category.getSlug(), e.getMessage());
            }
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
        var cats = dailyChallengeService.getAllCategories();
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
            Category category = dailyChallengeService.getCategoryById(dc.getCategoryId()).orElse(null);
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
        UUID playerId = assembler.playerIdFrom(principal);
        playerProfileService.ensureProfile(playerId);

        log.debug("Starting daily challenge for player {} in category '{}'", playerId, categorySlug);

        DailyChallengeService.GameStartRecord startRecord =
                dailyChallengeService.startDailyChallenge(playerId, categorySlug);

        Game game = startRecord.game();
        Question question = startRecord.question();

        assembler.loadScoreCache(question.getId());

        List<GameMove> moves = gameService.getMovesForGame(game.getId());

        log.info("Daily challenge game started: gameId={}, playerId={}, category={}, startingScore={}",
                game.getId(), playerId, categorySlug, startRecord.challenge().getStartingScore());

        return ResponseEntity.ok(assembler.buildGameStateResponse(game, question, startRecord.match(), moves));
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
        return gameEndpointHandler.submitAnswer(gameId, request, principal, httpRequest);
    }

    /**
     * Abandon an in-progress daily challenge game.
     */
    @PostMapping("/games/{gameId}/abandon")
    public ResponseEntity<Void> abandonGame(
            @PathVariable UUID gameId,
            Principal principal
    ) {
        return gameEndpointHandler.abandonGame(gameId, principal);
    }

    /**
     * Get current daily challenge game state including move history.
     */
    @GetMapping("/games/{gameId}")
    public ResponseEntity<GameStateResponse> getGameState(
            @PathVariable UUID gameId,
            Principal principal
    ) {
        return gameEndpointHandler.getGameState(gameId, principal);
    }

    /**
     * Get share data for a completed daily challenge game.
     */
    @GetMapping("/share/{gameId}")
    public ResponseEntity<DailyChallengeShareResponse> getShareData(@PathVariable UUID gameId) {
        return gameEndpointHandler.getShareData(gameId);
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
        return gameEndpointHandler.getGameAnswers(gameId, principal);
    }
}
