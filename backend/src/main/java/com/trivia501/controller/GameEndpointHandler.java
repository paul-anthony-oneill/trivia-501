package com.trivia501.controller;

import com.trivia501.dto.AnswerDebugResponse;
import com.trivia501.dto.DailyChallengeShareResponse;
import com.trivia501.dto.GameStateResponse;
import com.trivia501.dto.SubmitAnswerRequest;
import com.trivia501.dto.SubmitAnswerResponse;
import com.trivia501.model.*;
import com.trivia501.security.OptionalJwtFilter;
import com.trivia501.service.DailyChallengeService;
import com.trivia501.service.GameService;
import com.trivia501.service.MatchService;
import com.trivia501.service.QuestionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Shared game endpoint orchestration used by both {@link FreePlayController}
 * and {@link DailyChallengeController}.
 *
 * <p>Extracted to eliminate ~120 lines of duplicated handler code across the
 * three core game endpoints (submit, abandon, state retrieval) plus share-data
 * assembly. Both controllers become thin routers: extract Principal, delegate,
 * return the response.
 */
@Component
@Slf4j
public class GameEndpointHandler {

    private final GameService gameService;
    private final MatchService matchService;
    private final QuestionService questionService;
    private final DailyChallengeService dailyChallengeService;
    private final GameResponseAssembler assembler;

    public GameEndpointHandler(
            GameService gameService,
            MatchService matchService,
            QuestionService questionService,
            DailyChallengeService dailyChallengeService,
            GameResponseAssembler assembler
    ) {
        this.gameService = gameService;
        this.matchService = matchService;
        this.questionService = questionService;
        this.dailyChallengeService = dailyChallengeService;
        this.assembler = assembler;
    }

    // ── Submit answer ──────────────────────────────────────────────────────────

    public ResponseEntity<SubmitAnswerResponse> submitAnswer(
            UUID gameId,
            @Valid @RequestBody SubmitAnswerRequest request,
            Principal principal,
            HttpServletRequest httpRequest
    ) {
        UUID playerId = assembler.playerIdFrom(principal);
        log.debug("Submitting answer for game {}: '{}'", gameId, request.getAnswer());

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
                .gameState(assembler.buildGameStateResponse(game, question, match, result.usedAnswerIds(), List.of()))
                .build();

        log.debug("Answer processed: result={}, score={}->{}", move.getResult(),
                move.getScoreBefore(), move.getScoreAfter());

        return ResponseEntity.ok(response);
    }

    // ── Abandon game ───────────────────────────────────────────────────────────

    public ResponseEntity<Void> abandonGame(UUID gameId, Principal principal) {
        UUID playerId = assembler.playerIdFrom(principal);
        log.debug("Abandoning game {} for player {}", gameId, playerId);
        gameService.abandonGame(gameId, playerId);
        return ResponseEntity.noContent().build();
    }

    // ── Get game state ─────────────────────────────────────────────────────────

    public ResponseEntity<GameStateResponse> getGameState(UUID gameId, Principal principal) {
        UUID playerId = assembler.playerIdFrom(principal);
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

        return ResponseEntity.ok(assembler.buildGameStateResponse(game, question, match, moves));
    }

    // ── Share data ─────────────────────────────────────────────────────────────

    public ResponseEntity<DailyChallengeShareResponse> getShareData(UUID gameId) {
        Game game = gameService.getGameById(gameId).orElse(null);
        if (game == null) {
            return ResponseEntity.notFound().build();
        }

        List<GameMove> moves = gameService.getMovesForGame(gameId);

        DailyChallenge challenge = dailyChallengeService.findByChallengeDateAndQuestionId(
                LocalDate.now(), game.getQuestionId()).orElse(null);

        String categoryName = "Unknown";
        String categorySlug = "unknown";
        int startingScore = 501;
        LocalDate challengeDate = LocalDate.now();

        if (challenge != null) {
            startingScore = challenge.getStartingScore();
            challengeDate = challenge.getChallengeDate();
            Category category = dailyChallengeService.getCategoryById(challenge.getCategoryId()).orElse(null);
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

    // ── Debug: answer key (admin only) ─────────────────────────────────────────

    public ResponseEntity<List<AnswerDebugResponse>> getGameAnswers(UUID gameId, Principal principal) {
        UUID playerId = assembler.playerIdFrom(principal);
        return ResponseEntity.ok(gameService.getAnswersForGame(gameId, playerId));
    }
}
