package com.trivia501.service;

import com.trivia501.engine.AnswerEvaluator;
import com.trivia501.engine.AnswerResult;
import com.trivia501.engine.GameStateMachine;
import com.trivia501.engine.GameTransition;
import com.trivia501.model.Game;
import com.trivia501.model.GameMove;
import com.trivia501.model.Match;
import com.trivia501.repository.GameMoveRepository;
import com.trivia501.repository.GameRepository;
import com.trivia501.repository.MatchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
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
    private final AnswerEvaluator answerEvaluator;
    private final GameStateMachine gameStateMachine;
    private final PlayerProfileService playerProfileService;
    private final MatchService matchService;
    private final ResultSignerClient resultSignerClient;

    public GameService(
            GameRepository gameRepository,
            GameMoveRepository gameMoveRepository,
            MatchRepository matchRepository,
            AnswerEvaluator answerEvaluator,
            GameStateMachine gameStateMachine,
            PlayerProfileService playerProfileService,
            @Lazy MatchService matchService,
            ResultSignerClient resultSignerClient
    ) {
        this.gameRepository       = gameRepository;
        this.gameMoveRepository   = gameMoveRepository;
        this.matchRepository      = matchRepository;
        this.answerEvaluator      = answerEvaluator;
        this.gameStateMachine     = gameStateMachine;
        this.playerProfileService = playerProfileService;
        this.matchService         = matchService;
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
        int currentScore = getPlayerScore(game, match, playerId);

        AnswerResult answerResult = answerEvaluator.evaluateAnswer(
                game.getQuestionId(), answer, entityId, currentScore, usedAnswerIds);

        GameTransition transition = gameStateMachine.onMoveSubmitted(game, match, playerId, answerResult);

        GameMove move = buildMove(gameId, playerId, game.getTurnCount() + 1,
                answer, answerResult, transition, currentScore);
        gameMoveRepository.save(move);

        applyTransition(game, match, playerId, transition);
        gameRepository.save(game);

        if (transition.nextGameStatus() == Game.GameStatus.COMPLETED) {
            matchService.handleGameCompletion(game);
        }

        // Record game completion for authenticated players
        if (transition.moveResult() == GameMove.MoveResult.CHECKOUT) {
            playerProfileService.recordGameCompleted(playerId, transition.scoreAfter(), true);

            // Sign the result for verifiable share links. Failure is non-fatal.
            resultSignerClient.sign(gameId, playerId, transition.scoreAfter(), game.getCompletedAt() != null
                    ? game.getCompletedAt()
                    : java.time.LocalDateTime.now())
                    .ifPresent(token -> {
                        game.setResultToken(token);
                        gameRepository.save(game);
                    });
        }

        log.debug("Move processed: result={}, score {}→{}",
                transition.moveResult(), currentScore, transition.scoreAfter());

        return new MoveRecord(move, game, match, usedAnswerIds, answerResult.getReason());
    }

    /**
     * Handle a player timeout.
     *
     * @param gameId   the game UUID
     * @param playerId the player who timed out
     * @throws IllegalStateException if the game is not in progress
     */
    @Transactional
    public void handleTimeout(UUID gameId, UUID playerId) {
        log.debug("Handling timeout for game {} by player {}", gameId, playerId);

        Game game   = getGameOrThrow(gameId);
        Match match = getMatchOrThrow(game.getMatchId());

        validateGameInProgress(game);

        int currentScore = getPlayerScore(game, match, playerId);
        GameTransition transition = gameStateMachine.onTimeout(game, match, playerId);

        GameMove timeoutMove = GameMove.builder()
                .gameId(gameId)
                .playerId(playerId)
                .moveNumber(game.getTurnCount() + 1)
                .submittedAnswer("")
                .result(GameMove.MoveResult.TIMEOUT)
                .scoreBefore(currentScore)
                .scoreAfter(currentScore)
                .isTimeout(true)
                .build();
        gameMoveRepository.save(timeoutMove);

        applyTransition(game, match, playerId, transition);
        gameRepository.save(game);

        if (transition.nextGameStatus() == Game.GameStatus.COMPLETED) {
            matchService.handleGameCompletion(game);
        }
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
        boolean isPlayer2 = match.getPlayer2Id() != null && playerId.equals(match.getPlayer2Id());
        if (!isPlayer1 && !isPlayer2) {
            throw new IllegalStateException("Player " + playerId + " is not part of match " + match.getId());
        }

        if (game.getStatus() == Game.GameStatus.IN_PROGRESS) {
            game.setStatus(Game.GameStatus.ABANDONED);
            game.setCompletedAt(java.time.LocalDateTime.now());
            gameRepository.save(game);
        }

        if (match.getStatus() == Match.MatchStatus.IN_PROGRESS) {
            match.setStatus(Match.MatchStatus.ABANDONED);
            match.setCompletedAt(java.time.LocalDateTime.now());
            matchRepository.save(match);
        }

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
                .player2Score(startingScore)
                .player1ConsecutiveTimeouts(0)
                .player2ConsecutiveTimeouts(0)
                .turnCount(0)
                .turnTimerSeconds(GameStateMachine.DEFAULT_TIMER)
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
     * Abandon all in-progress games for a player.
     * Safety net that prevents orphaned-game accumulation when a player
     * starts a new game without explicitly abandoning the old one.
     */
    @Transactional
    public void abandonActiveGamesForPlayer(UUID playerId) {
        List<Game> activeGames = gameRepository.findActiveGamesByPlayerId(playerId);
        for (Game game : activeGames) {
            log.info("Abandoning orphaned game {} for player {} before new game", game.getId(), playerId);
            game.setStatus(Game.GameStatus.ABANDONED);
            game.setCompletedAt(java.time.LocalDateTime.now());
            gameRepository.save(game);

            Match match = matchRepository.findById(game.getMatchId()).orElse(null);
            if (match != null && match.getStatus() == Match.MatchStatus.IN_PROGRESS) {
                match.setStatus(Match.MatchStatus.ABANDONED);
                match.setCompletedAt(java.time.LocalDateTime.now());
                matchRepository.save(match);
            }
        }
    }

    /**
     * Abandon a batch of stale games (used by the scheduled cleanup task).
     */
    @Transactional
    public void abandonStaleGames(List<Game> staleGames) {
        for (Game game : staleGames) {
            log.info("Stale-game cleanup: abandoning game {} (last activity: {})", game.getId(), game.getUpdatedAt());
            game.setStatus(Game.GameStatus.ABANDONED);
            game.setCompletedAt(java.time.LocalDateTime.now());
            gameRepository.save(game);

            Match match = matchRepository.findById(game.getMatchId()).orElse(null);
            if (match != null && match.getStatus() == Match.MatchStatus.IN_PROGRESS) {
                match.setStatus(Match.MatchStatus.ABANDONED);
                match.setCompletedAt(java.time.LocalDateTime.now());
                matchRepository.save(match);
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Apply a {@link GameTransition} to the mutable {@link Game} entity.
     *
     * <p>This is the single place in the service layer that writes game state.
     * All business rules about <em>what</em> should change have already been decided
     * by {@link GameStateMachine}; this method only applies the decision.
     */
    private void applyTransition(Game game, Match match, UUID activePlayerId, GameTransition t) {
        // Update the active player's score
        if (activePlayerId.equals(match.getPlayer1Id())) {
            game.setPlayer1Score(t.scoreAfter());
        } else if (match.getPlayer2Id() != null && activePlayerId.equals(match.getPlayer2Id())) {
            game.setPlayer2Score(t.scoreAfter());
        }

        // Update game status and winner
        game.setStatus(t.nextGameStatus());
        if (t.winnerId() != null) {
            game.setWinnerId(t.winnerId());
        }

        // Update whose turn it is
        if (t.nextTurnPlayerId() != null) {
            game.setCurrentTurnPlayerId(t.nextTurnPlayerId());
        }

        // Advance turn counter if the move consumed a turn
        if (t.turnAdvanced()) {
            game.setTurnCount(game.getTurnCount() + 1);
        }

        // Update timer and consecutive-timeout counters
        game.setTurnTimerSeconds(t.nextTimerSeconds());
        game.setPlayer1ConsecutiveTimeouts(t.player1ConsecutiveTimeouts());
        game.setPlayer2ConsecutiveTimeouts(t.player2ConsecutiveTimeouts());
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
                .isTimeout(false)
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

    private int getPlayerScore(Game game, Match match, UUID playerId) {
        if (playerId.equals(match.getPlayer1Id())) return game.getPlayer1Score();
        if (match.getPlayer2Id() != null && playerId.equals(match.getPlayer2Id())) return game.getPlayer2Score();
        throw new IllegalArgumentException("Player " + playerId + " is not part of this match");
    }
}
