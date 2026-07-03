package com.trivia501.service;

import com.trivia501.engine.AnswerEvaluator;
import com.trivia501.engine.AnswerResult;
import com.trivia501.engine.GameStateMachine;
import com.trivia501.engine.GameTransition;
import com.trivia501.event.GameCompletedEvent;
import com.trivia501.model.Game;
import com.trivia501.model.GameMove;
import com.trivia501.model.Match;
import com.trivia501.repository.AnswerRepository;
import com.trivia501.repository.GameMoveRepository;
import com.trivia501.repository.GameRepository;
import com.trivia501.repository.MatchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates game-play operations: loading, validating, calling the engine, and persisting.
 *
 * <h3>Responsibility boundary</h3>
 * <ul>
 *   <li>This service owns the <em>database lifecycle</em>: load → validate → save.</li>
 *   <li>All game-state <em>transition rules</em> live in {@link GameStateMachine}.
 *       This service never encodes rule logic inline — it only calls the machine and
 *       applies the returned {@link GameTransition}.</li>
 *   <li>Controllers and WebSocket handlers are thin dispatchers that call this service
 *       and return the resulting DTOs to the client.</li>
 * </ul>
 */
@Service
@Slf4j
public class GameService {

    /**
     * Result bundle returned by {@link #processPlayerMove}.
     * Carries the already-loaded entities so callers don't need to re-fetch them.
     */
    public record MoveRecord(GameMove move, Game game, Match match, List<UUID> usedAnswerIds, String reason) {}

    private final GameRepository gameRepository;
    private final GameMoveRepository gameMoveRepository;
    private final MatchRepository matchRepository;
    private final AnswerRepository answerRepository;
    private final AnswerEvaluator answerEvaluator;
    private final GameStateMachine gameStateMachine;
    private final GameHintsService gameHintsService;
    private final PlayerProfileService playerProfileService;
    private final ApplicationEventPublisher eventPublisher;
    private final ResultSignerClient resultSignerClient;

    public GameService(
            GameRepository gameRepository,
            GameMoveRepository gameMoveRepository,
            MatchRepository matchRepository,
            AnswerRepository answerRepository,
            AnswerEvaluator answerEvaluator,
            GameStateMachine gameStateMachine,
            GameHintsService gameHintsService,
            PlayerProfileService playerProfileService,
            ApplicationEventPublisher eventPublisher,
            ResultSignerClient resultSignerClient
    ) {
        this.gameRepository       = gameRepository;
        this.gameMoveRepository   = gameMoveRepository;
        this.matchRepository      = matchRepository;
        this.answerRepository     = answerRepository;
        this.answerEvaluator      = answerEvaluator;
        this.gameStateMachine     = gameStateMachine;
        this.gameHintsService     = gameHintsService;
        this.playerProfileService = playerProfileService;
        this.eventPublisher       = eventPublisher;
        this.resultSignerClient   = resultSignerClient;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Process a player's answer submission.
     *
     * <ol>
     *   <li>Load and validate the game/match state.</li>
     *   <li>Evaluate the answer via {@link AnswerEvaluator}.</li>
     *   <li>Delegate transition logic to {@link GameStateMachine}.</li>
     *   <li>Persist the resulting move and updated game state.</li>
     * </ol>
     *
     * @param gameId   the game UUID
     * @param playerId the player making the move
     * @param answer   the submitted answer text (for display/history)
     * @param entityId the entity UUID from the autocomplete dropdown, or null
     * @return a {@link MoveRecord} with the persisted move, updated game, match, and used answer IDs
     * @throws IllegalStateException    if the game is not in progress or it is not this player's turn
     * @throws IllegalArgumentException if the game or match does not exist
     */
    @Transactional
    public MoveRecord processPlayerMove(UUID gameId, UUID playerId, String answer, UUID entityId) {
        log.debug("Processing move for game {} by player {}: {} (entityId={})", gameId, playerId, answer, entityId);

        Game game   = getGameOrThrow(gameId);
        Match match = getMatchOrThrow(game.getMatchId());

        validateGameInProgress(game);
        validatePlayerTurn(game, playerId);

        List<UUID> usedAnswerIds = gameMoveRepository.findUsedAnswerIdsByGameId(gameId);
        int currentScore = game.getPlayer1Score();

        AnswerResult answerResult = answerEvaluator.evaluateAnswer(
                game.getQuestionId(), answer, entityId, currentScore, usedAnswerIds);

        GameTransition transition = gameStateMachine.onMoveSubmitted(game, match, playerId, answerResult);

        GameMove move = buildMove(gameId, playerId, game.getTurnCount() + 1,
                answer, answerResult, transition, currentScore);
        gameMoveRepository.save(move);

        applyTransition(game, match, playerId, transition);
        gameRepository.save(game);

        if (transition.nextGameStatus() == Game.GameStatus.COMPLETED) {
            eventPublisher.publishEvent(new GameCompletedEvent(
                    game.getId(), game.getMatchId(), game.getWinnerId(),
                    transition.moveResult() == GameMove.MoveResult.CHECKOUT));
        }
        if (transition.moveResult() == GameMove.MoveResult.CHECKOUT) {
            playerProfileService.recordGameCompleted(playerId, transition.scoreAfter(), true);

            // Sign the result for verifiable share links. Failure is non-fatal.
            resultSignerClient.sign(gameId, playerId, transition.scoreAfter(), game.getCompletedAt())
                    .ifPresent(token -> {
                        game.setResultToken(token);
                        gameRepository.save(game);
                    });
        }

        // ── Bust-out detection: game over if no playable moves remain ────────
        if (game.getStatus() == Game.GameStatus.IN_PROGRESS) {
            List<UUID> postMoveUsedIds = usedAnswerIds;
            if (answerResult.getAnswerId() != null) {
                postMoveUsedIds = new java.util.ArrayList<>(usedAnswerIds);
                postMoveUsedIds.add(answerResult.getAnswerId());
            }

            int viableMaxScore = game.getPlayer1Score() + 10; // scores ≤ this won't bust below -10
            if (!answerRepository.hasViableMove(game.getQuestionId(), viableMaxScore, postMoveUsedIds)) {
                log.info("Bust-out: no playable moves remain for game {} at score {}",
                        gameId, game.getPlayer1Score());
                game.setStatus(Game.GameStatus.COMPLETED);
                game.setCompletedAt(java.time.LocalDateTime.now());
                gameRepository.save(game);

                eventPublisher.publishEvent(new GameCompletedEvent(
                        game.getId(), game.getMatchId(), null, false));
                playerProfileService.recordGameCompleted(playerId, game.getPlayer1Score(), false);
            }
        }

        // Evict the score cache when the game ends so future games
        // on the same question see fresh data after re-materializations.
        if (game.getStatus() == Game.GameStatus.COMPLETED) {
            gameHintsService.evictScoreCache(game.getQuestionId());
        }

        // Build the post-move used-answer list for accurate hint computation.
        // usedAnswerIds is the pre-move list; the just-consumed answer should
        // also be excluded from checkout/max-score counts.
        List<UUID> hintExcludeIds = usedAnswerIds;
        if (answerResult.getAnswerId() != null) {
            hintExcludeIds = new java.util.ArrayList<>(usedAnswerIds);
            hintExcludeIds.add(answerResult.getAnswerId());
        }

        log.debug("Move processed: result={}, score {}→{}",
                transition.moveResult(), currentScore, transition.scoreAfter());

        return new MoveRecord(move, game, match, hintExcludeIds, answerResult.getReason());
    }

    /**
     * Abandon an in-progress game and its parent match.
     *
     * <p>Safe to call when the game is already completed or abandoned — it
     * becomes a no-op in that case so the frontend can fire-and-forget.
     *
     * @param gameId   the game UUID
     * @param playerId the player requesting abandonment (must be part of the match)
     * @throws IllegalArgumentException if the game or match does not exist
     * @throws IllegalStateException    if the player is not part of the match
     */
    @Transactional
    public void abandonGame(UUID gameId, UUID playerId) {
        log.debug("Abandoning game {} for player {}", gameId, playerId);

        Game game   = getGameOrThrow(gameId);
        Match match = getMatchOrThrow(game.getMatchId());

        boolean isPlayer1 = playerId.equals(match.getPlayer1Id());
        if (!isPlayer1) {
            throw new IllegalStateException("Player " + playerId + " is not part of match " + match.getId());
        }

        abandonGameAndMatch(game);

        log.info("Game abandoned: gameId={}, matchId={}", gameId, match.getId());
    }

    /**
     * Create a new game within an existing match.
     *
     * @param matchId       the match UUID
     * @param questionId    the question UUID
     * @param gameNumber    the ordinal position within the match (1-based)
     * @param startingScore the starting score for both players (default 501 for standard play)
     * @return the persisted {@link Game}
     */
    @Transactional
    public Game createGame(UUID matchId, UUID questionId, int gameNumber, int startingScore) {
        log.debug("Creating game {} for match {} (starting score: {})", gameNumber, matchId, startingScore);

        Match match = getMatchOrThrow(matchId);

        Game game = Game.builder()
                .matchId(matchId)
                .gameNumber(gameNumber)
                .questionId(questionId)
                .status(Game.GameStatus.IN_PROGRESS)
                .currentTurnPlayerId(match.getPlayer1Id()) // Player 1 always goes first
                .player1Score(startingScore)
                .turnCount(0)
                .build();

        return gameRepository.save(game);
    }

    /**
     * Return the set of answer UUIDs already used in a game (prevents duplicate submissions).
     *
     * @param gameId the game UUID
     * @return list of used answer UUIDs
     */
    @Transactional(readOnly = true)
    public List<UUID> getUsedAnswerIds(UUID gameId) {
        return gameMoveRepository.findUsedAnswerIdsByGameId(gameId);
    }

    /**
     * Look up a game by ID.
     *
     * @param gameId the game UUID
     * @return an optional containing the game if it exists
     */
    @Transactional(readOnly = true)
    public Optional<Game> getGameById(UUID gameId) {
        return gameRepository.findById(gameId);
    }

    /**
     * Find the most recent in-progress game for a player.
     */
    @Transactional(readOnly = true)
    public Optional<Game> findActiveGameForPlayer(UUID playerId) {
        return gameRepository.findActiveGameByPlayerId(playerId);
    }

    /**
     * Get all moves for a game ordered by move number (ascending).
     */
    @Transactional(readOnly = true)
    public List<GameMove> getMovesForGame(UUID gameId) {
        return gameMoveRepository.findByGameIdOrderByMoveNumberAsc(gameId);
    }

    /**
     * Return all answers for a game's question (debug/admin endpoint).
     * Validates that the requesting player owns the game.
     */
    @Transactional(readOnly = true)
    public List<com.trivia501.dto.AnswerDebugResponse> getAnswersForGame(UUID gameId, UUID playerId) {
        Game game = getGameOrThrow(gameId);
        Match match = getMatchOrThrow(game.getMatchId());

        if (!playerId.equals(match.getPlayer1Id())) {
            throw new IllegalArgumentException("Player does not own game " + gameId);
        }

        return answerRepository.findByQuestionIdOrderByScoreDesc(game.getQuestionId())
                .stream()
                .map(a -> new com.trivia501.dto.AnswerDebugResponse(
                        a.getId(), a.getDisplayText(), a.getScore(),
                        a.getIsValidDarts(), a.getIsBust()))
                .toList();
    }

    /**
     * Abandon all in-progress games for a player.
     * Safety net that prevents orphaned-game accumulation when a player
     * starts a new game without explicitly abandoning the old one.
     */
    @Transactional
    public void abandonActiveGamesForPlayer(UUID playerId) {
        List<Game> activeGames = gameRepository.findActiveGamesByPlayerId(playerId);
        for (Game game : activeGames) {
            log.info("Abandoning orphaned game {} for player {} before new game", game.getId(), playerId);
            abandonGameAndMatch(game);
        }
    }

    /**
     * Abandon a batch of stale games (used by the scheduled cleanup task).
     */
    @Transactional
    public void abandonStaleGames(List<Game> staleGames) {
        for (Game game : staleGames) {
            log.info("Stale-game cleanup: abandoning game {} (last activity: {})", game.getId(), game.getUpdatedAt());
            abandonGameAndMatch(game);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Mark a game and its parent match as ABANDONED.
     * Idempotent — safe to call on already-completed or already-abandoned rows.
     */
    private void abandonGameAndMatch(Game game) {
        if (game.getStatus() == Game.GameStatus.IN_PROGRESS) {
            game.setStatus(Game.GameStatus.ABANDONED);
            game.setCompletedAt(java.time.LocalDateTime.now());
            gameRepository.save(game);
        }

        Match match = matchRepository.findById(game.getMatchId()).orElse(null);
        if (match != null && match.getStatus() == Match.MatchStatus.IN_PROGRESS) {
            match.setStatus(Match.MatchStatus.ABANDONED);
            match.setCompletedAt(java.time.LocalDateTime.now());
            matchRepository.save(match);
        }
    }

    /**
     * Apply a {@link GameTransition} to the mutable {@link Game} entity.
     *
     * <p>This is the single place in the service layer that writes game state.
     * All business rules about <em>what</em> should change have already been decided
     * by {@link GameStateMachine}; this method only applies the decision.
     */
    private void applyTransition(Game game, Match match, UUID activePlayerId, GameTransition t) {
        // Update the player's score
        game.setPlayer1Score(t.scoreAfter());

        // Update game status and winner
        game.setStatus(t.nextGameStatus());
        if (t.winnerId() != null) {
            game.setWinnerId(t.winnerId());
        }

        // Stamp completed_at when the game ends (CHECKOUT or bust-out).
        // The bust-out path in processPlayerMove also sets this, but the
        // CHECKOUT path only passes through here — and completedAt must not
        // be null for downstream consumers (signer, stats, share data).
        if (t.nextGameStatus() == Game.GameStatus.COMPLETED && game.getCompletedAt() == null) {
            game.setCompletedAt(java.time.LocalDateTime.now());
        }

        // Update whose turn it is
        if (t.nextTurnPlayerId() != null) {
            game.setCurrentTurnPlayerId(t.nextTurnPlayerId());
        }

        // Advance turn counter if the move consumed a turn
        if (t.turnAdvanced()) {
            game.setTurnCount(game.getTurnCount() + 1);
        }
    }

    /** Build a {@link GameMove} from a transition result. */
    private GameMove buildMove(
            UUID gameId,
            UUID playerId,
            int moveNumber,
            String submittedAnswer,
            AnswerResult answerResult,
            GameTransition transition,
            int scoreBefore
    ) {
        return GameMove.builder()
                .gameId(gameId)
                .playerId(playerId)
                .moveNumber(moveNumber)
                .submittedAnswer(submittedAnswer)
                .matchedAnswerId(answerResult.getAnswerId())
                .matchedDisplayText(answerResult.getDisplayText())
                .result(transition.moveResult())
                .scoreValue(answerResult.getScore())
                .scoreBefore(scoreBefore)
                .scoreAfter(transition.scoreAfter())
                .build();
    }

    private Game getGameOrThrow(UUID gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found: " + gameId));
    }

    private Match getMatchOrThrow(UUID matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));
    }

    private void validateGameInProgress(Game game) {
        if (game.getStatus() != Game.GameStatus.IN_PROGRESS) {
            throw new IllegalStateException("Game is not in progress");
        }
    }

    private void validatePlayerTurn(Game game, UUID playerId) {
        if (!game.getCurrentTurnPlayerId().equals(playerId)) {
            throw new IllegalStateException("Not player's turn");
        }
    }
}
