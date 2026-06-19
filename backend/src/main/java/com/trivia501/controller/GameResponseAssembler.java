package com.trivia501.controller;

import com.trivia501.dto.GameHints;
import com.trivia501.dto.GameStateResponse;
import com.trivia501.dto.MoveDto;
import com.trivia501.model.EntityType;
import com.trivia501.model.Game;
import com.trivia501.model.GameMove;
import com.trivia501.model.Match;
import com.trivia501.model.Question;
import com.trivia501.service.GameHintsService;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Shared response-assembly logic used by both {@link DailyChallengeController}
 * and {@link FreePlayController}. Extracted to eliminate ~153 lines of
 * verbatim duplication between the two controllers.
 */
@Component
public class GameResponseAssembler {

    private final GameHintsService gameHintsService;

    public GameResponseAssembler(GameHintsService gameHintsService) {
        this.gameHintsService = gameHintsService;
    }

    public UUID playerIdFrom(Principal principal) {
        return UUID.fromString(principal.getName());
    }

    public GameStateResponse buildGameStateResponse(Game game, Question question, Match match,
                                                     List<GameMove> moves) {
        return buildGameStateResponse(game, question, match, null, moves);
    }

    public GameStateResponse buildGameStateResponse(Game game, Question question, Match match,
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

    /** Pass-through to {@code GameHintsService.loadScoreCache()}. */
    public void loadScoreCache(UUID questionId) {
        gameHintsService.loadScoreCache(questionId);
    }

    public MoveDto toMoveDto(GameMove move) {
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
