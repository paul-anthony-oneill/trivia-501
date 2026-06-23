package com.trivia501.controller;

import com.trivia501.dto.AnswerDebugResponse;
import com.trivia501.dto.FootballFilter;
import com.trivia501.dto.GameStateResponse;
import com.trivia501.dto.StartFreePlayRequest;
import com.trivia501.dto.SubmitAnswerRequest;
import com.trivia501.dto.SubmitAnswerResponse;
import com.trivia501.model.*;
import com.trivia501.service.GameService;
import com.trivia501.service.MatchService;
import com.trivia501.service.PlayerProfileService;
import com.trivia501.service.QuestionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for Free Play (single-player) game mode.
 *
 * <h3>Authentication</h3>
 * Player identity is derived from the authenticated {@link Principal} rather
 * than an explicit {@code playerId} request parameter.  In development and
 * test environments {@link com.trivia501.security.OptionalJwtFilter}
 * provides a fixed principal automatically.  In production the same filter
 * validates Supabase JWTs.
 */
@RestController
@RequestMapping("/api/freeplay")
@Slf4j
public class FreePlayController {

    private final MatchService matchService;
    private final GameService gameService;
    private final QuestionService questionService;
    private final PlayerProfileService playerProfileService;
    private final GameResponseAssembler assembler;
    private final GameEndpointHandler gameEndpointHandler;

    private static final String DEFAULT_CATEGORY_SLUG = CategorySlug.FOOTBALL;

    public FreePlayController(
        MatchService matchService,
        GameService gameService,
        QuestionService questionService,
        PlayerProfileService playerProfileService,
        GameResponseAssembler assembler,
        GameEndpointHandler gameEndpointHandler
    ) {
        this.matchService = matchService;
        this.gameService = gameService;
        this.questionService = questionService;
        this.playerProfileService = playerProfileService;
        this.assembler = assembler;
        this.gameEndpointHandler = gameEndpointHandler;
    }

    @PostMapping("/start")
    public ResponseEntity<GameStateResponse> startFreePlayGame(
        @Valid @RequestBody StartFreePlayRequest request,
        Principal principal
    ) {
        UUID playerId = assembler.playerIdFrom(principal);
        playerProfileService.ensureProfile(playerId);

        log.debug("Starting Free Play game for player {}", playerId);

        gameService.abandonActiveGamesForPlayer(playerId);

        String categorySlug = request.getCategorySlug() != null
            ? request.getCategorySlug()
            : DEFAULT_CATEGORY_SLUG;

        Category category = questionService.getCategoryBySlug(categorySlug)
            .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categorySlug));

        Match match = matchService.createMatch(
            playerId,
            category.getId(),
            Match.MatchType.CASUAL,
            Match.MatchFormat.BEST_OF_1,
            request.getDifficulty()
        );

        int startingScore = request.getStartingScore() != null ? request.getStartingScore() : 501;

        FootballFilter filter = request.getFootballFilter();
        if (filter != null && filter.getScope() != null) {
            log.debug("Football filter supplied: scope={}, league={}, club={}, stat={}",
                filter.getScope(), filter.getLeague(), filter.getClub(), filter.getStatType());
        }

        MatchService.GameStartRecord startRecord = filter != null && filter.getScope() != null
            ? matchService.startNextGameWithFilter(match, startingScore, filter)
            : matchService.startNextGame(match, startingScore);
        Game game = startRecord.game();
        Question question = startRecord.question();

        assembler.loadScoreCache(question.getId());

        log.info("Free Play game started: gameId={}, playerId={}", game.getId(), playerId);

        return ResponseEntity.ok(assembler.buildGameStateResponse(game, question, match, List.of(), List.of()));
    }

    @PostMapping("/games/{gameId}/submit")
    public ResponseEntity<SubmitAnswerResponse> submitAnswer(
        @PathVariable UUID gameId,
        @Valid @RequestBody SubmitAnswerRequest request,
        Principal principal,
        HttpServletRequest httpRequest
    ) {
        return gameEndpointHandler.submitAnswer(gameId, request, principal, httpRequest);
    }

    @PostMapping("/games/{gameId}/abandon")
    public ResponseEntity<Void> abandonGame(
        @PathVariable UUID gameId,
        Principal principal
    ) {
        return gameEndpointHandler.abandonGame(gameId, principal);
    }

    @GetMapping("/games/{gameId}")
    public ResponseEntity<GameStateResponse> getGameState(
        @PathVariable UUID gameId,
        Principal principal
    ) {
        return gameEndpointHandler.getGameState(gameId, principal);
    }

    @GetMapping("/games/active")
    public ResponseEntity<GameStateResponse> getActiveGame(Principal principal) {
        UUID playerId = assembler.playerIdFrom(principal);
        log.debug("Looking up active game for player {}", playerId);

        Game game = gameService.findActiveGameForPlayer(playerId).orElse(null);

        if (game == null) {
            return ResponseEntity.notFound().build();
        }

        Match match = matchService.getMatchById(game.getMatchId())
            .orElseThrow(() -> new IllegalStateException("Match not found"));

        Question question = questionService.getQuestionById(game.getQuestionId())
            .orElseThrow(() -> new IllegalStateException("Question not found"));

        List<GameMove> moves = gameService.getMovesForGame(game.getId());

        log.info("Active game found for player {}: gameId={}", playerId, game.getId());

        return ResponseEntity.ok(assembler.buildGameStateResponse(game, question, match, moves));
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Principal principal) {
        UUID playerId = assembler.playerIdFrom(principal);
        return playerProfileService.findByPlayerId(playerId)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
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
