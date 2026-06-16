package com.trivia501.controller;

import com.trivia501.dto.AnswerDebugResponse;
import com.trivia501.dto.FootballFilter;
import com.trivia501.dto.GameHints;
import com.trivia501.dto.GameStateResponse;
import com.trivia501.dto.MoveDto;
import com.trivia501.dto.StartFreePlayRequest;
import com.trivia501.dto.SubmitAnswerRequest;
import com.trivia501.dto.SubmitAnswerResponse;
import com.trivia501.model.*;
import com.trivia501.repository.AnswerRepository;
import com.trivia501.service.GameHintsService;
import com.trivia501.service.GameService;
import com.trivia501.service.MatchService;
import com.trivia501.service.QuestionService;
import com.trivia501.security.OptionalJwtFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
 *
 * Endpoints:
 * <ul>
 *   <li>POST /api/freeplay/start              — Start a new Free Play game</li>
 *   <li>POST /api/freeplay/games/{id}/submit  — Submit an answer</li>
 *   <li>POST /api/freeplay/games/{id}/abandon — Abandon a game</li>
 *   <li>GET  /api/freeplay/games/{id}         — Get current game state</li>
 *   <li>GET  /api/freeplay/games/active       — Get player's active game</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/freeplay")
@Slf4j
public class FreePlayController {

    private final MatchService matchService;
    private final GameService gameService;
    private final QuestionService questionService;
    private final GameHintsService gameHintsService;
    private final com.trivia501.service.PlayerProfileService playerProfileService;
    private final AnswerRepository answerRepository;

    private static final String DEFAULT_CATEGORY_SLUG = CategorySlug.FOOTBALL;

    public FreePlayController(
        MatchService matchService,
        GameService gameService,
        QuestionService questionService,
        GameHintsService gameHintsService,
        com.trivia501.service.PlayerProfileService playerProfileService,
        AnswerRepository answerRepository
    ) {
        this.matchService = matchService;
        this.gameService = gameService;
        this.questionService = questionService;
        this.gameHintsService = gameHintsService;
        this.playerProfileService = playerProfileService;
        this.answerRepository = answerRepository;
    }

    /**
     * Start a new Free Play game.
     *
     * <p>Player identity is read from the authenticated principal — the client
     * cannot supply or override the player ID.
     *
     * <p>Any existing in-progress games for the player are abandoned first to
     * prevent orphaned rows.
     *
     * @param request   optional category / difficulty preferences
     * @param principal injected by Spring Security from the current auth token
     * @return initial game state
     */
    @PostMapping("/start")
    public ResponseEntity<GameStateResponse> startFreePlayGame(
        @Valid @RequestBody StartFreePlayRequest request,
        Principal principal
    ) {
        UUID playerId = playerIdFrom(principal);
        playerProfileService.ensureProfile(playerId);

        log.debug("Starting Free Play game for player {}", playerId);

        // Prevent orphaned-game accumulation: abandon any in-progress games
        gameService.abandonActiveGamesForPlayer(playerId);

        String categorySlug = request.getCategorySlug() != null
            ? request.getCategorySlug()
            : DEFAULT_CATEGORY_SLUG;

        Category category = questionService.getCategoryBySlug(categorySlug)
            .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categorySlug));

        Match match = matchService.createMatch(
            playerId,
            null,
            category.getId(),
            Match.MatchType.CASUAL,
            Match.MatchFormat.BEST_OF_1,
            request.getDifficulty()
        );

        int startingScore = request.getStartingScore() != null ? request.getStartingScore() : 501;

        // If a football filter is supplied, resolve it to a specific question now.
        // startNextGame uses this pre-selected question instead of picking randomly.
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

        gameHintsService.loadScoreCache(question.getId());

        log.info("Free Play game started: gameId={}, playerId={}", game.getId(), playerId);

        return ResponseEntity.ok(buildGameStateResponse(game, question, match, List.of(), List.of()));
    }

    /**
     * Submit an answer for the current game.
     *
     * <p>Player identity comes from the authenticated principal; the caller
     * cannot spoof another player's ID.
     *
     * @param gameId    the game UUID
     * @param request   the answer text
     * @param principal injected by Spring Security
     * @return move result and updated game state
     */
    @PostMapping("/games/{gameId}/submit")
    public ResponseEntity<SubmitAnswerResponse> submitAnswer(
        @PathVariable UUID gameId,
        @Valid @RequestBody SubmitAnswerRequest request,
        Principal principal,
        HttpServletRequest httpRequest
    ) {
        UUID playerId = playerIdFrom(principal);
        log.debug("Submitting answer for game {}: '{}'", gameId, request.getAnswer());

        GameService.MoveRecord result = gameService.processPlayerMove(gameId, playerId, request.getAnswer(), request.getEntityId());

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

        log.debug("Answer processed: result={}, score={}->{}", move.getResult(),
            move.getScoreBefore(), move.getScoreAfter());

        return ResponseEntity.ok(response);
    }

    /**
     * Abandon an in-progress game.
     *
     * <p>Idempotent: calling this on an already-completed or already-abandoned
     * game is safe — the service treats it as a no-op.
     *
     * @param gameId    the game UUID
     * @param principal injected by Spring Security
     * @return 204 No Content on success
     */
    @PostMapping("/games/{gameId}/abandon")
    public ResponseEntity<Void> abandonGame(
        @PathVariable UUID gameId,
        Principal principal
    ) {
        UUID playerId = playerIdFrom(principal);
        log.debug("Abandoning game {} for player {}", gameId, playerId);
        gameService.abandonGame(gameId, playerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get current game state including move history.
     *
     * <p>Player identity comes from the authenticated principal.
     *
     * @param gameId    the game UUID
     * @param principal injected by Spring Security
     * @return current game state with moves, or 404 if the game does not exist
     */
    @GetMapping("/games/{gameId}")
    public ResponseEntity<GameStateResponse> getGameState(
        @PathVariable UUID gameId,
        Principal principal
    ) {
        UUID playerId = playerIdFrom(principal);
        log.debug("Getting game state for game {} (requestedBy={})", gameId, playerId);

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
     * Get the current player's active in-progress game (if any).
     *
     * <p>This is the primary recovery endpoint — the frontend calls it on mount
     * after a page refresh to discover any game left in progress. If no active
     * game exists, a 404 is returned and the frontend shows the lobby.
     *
     * @param principal injected by Spring Security
     * @return current game state with moves, or 404 if no active game
     */
    @GetMapping("/games/active")
    public ResponseEntity<GameStateResponse> getActiveGame(Principal principal) {
        UUID playerId = playerIdFrom(principal);
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

        return ResponseEntity.ok(buildGameStateResponse(game, question, match, moves));
    }

    /**
     * Returns the player's profile if they are authenticated (has a real JWT).
     * Returns 404 for anonymous users.
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Principal principal) {
        UUID playerId = playerIdFrom(principal);
        return playerProfileService.findByPlayerId(playerId)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Debug endpoint: returns all answers for the game's question.
     * Used by the in-game debug panel to surface valid checkout targets.
     */
    @GetMapping("/games/{gameId}/answers")
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

    /**
     * Parses the authenticated principal name as a {@link UUID}.
     * The principal name is the player's UUID string set by the auth filter
     * (via {@link com.trivia501.security.OptionalJwtFilter}).
     */
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
