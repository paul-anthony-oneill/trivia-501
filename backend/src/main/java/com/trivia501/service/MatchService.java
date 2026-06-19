package com.trivia501.service;

import com.trivia501.dto.FootballFilter;
import com.trivia501.event.GameCompletedEvent;
import com.trivia501.model.Game;
import com.trivia501.model.Match;
import com.trivia501.model.Question;
import com.trivia501.repository.GameRepository;
import com.trivia501.repository.MatchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing matches and match orchestration.
 *
 * Responsibilities:
 * - Create new matches
 * - Start and manage games within matches
 * - Track game wins per player
 * - Determine match winners (best-of-1/3/5 logic)
 * - Handle match completion
 * - Provide match statistics
 */
@Service
@Slf4j
public class MatchService {

    /**
     * Result bundle returned by {@link #startNextGame}.
     * Carries the created game and the question it was drawn for,
     * so callers don't need to re-fetch the question.
     */
    public record GameStartRecord(Game game, Question question) {}

    private final MatchRepository matchRepository;
    private final GameRepository gameRepository;
    private final GameService gameService;
    private final QuestionService questionService;

    public MatchService(
        MatchRepository matchRepository,
        GameRepository gameRepository,
        GameService gameService,
        QuestionService questionService
    ) {
        this.matchRepository = matchRepository;
        this.gameRepository = gameRepository;
        this.gameService = gameService;
        this.questionService = questionService;
    }

    /**
     * Create a new solo match.
     *
     * @param player1Id the player UUID
     * @param categoryId the category UUID
     * @param type the match type
     * @param format the match format
     * @return the created match
     */
    @Transactional
    public Match createMatch(
        UUID player1Id,
        UUID categoryId,
        Match.MatchType type,
        Match.MatchFormat format,
        Integer difficulty
    ) {
        log.debug("Creating match: player1={}, format={}, difficulty={}",
            player1Id, format, difficulty);

        Match match = Match.builder()
            .player1Id(player1Id)
            .categoryId(categoryId)
            .type(type)
            .format(format)
            .difficulty(difficulty != null ? difficulty : 2)
            .status(Match.MatchStatus.IN_PROGRESS)
            .player1GamesWon(0)
            .build();

        Match savedMatch = matchRepository.save(match);
        log.info("Match created: id={}, format={}, difficulty={}",
            savedMatch.getId(), format, difficulty);

        return savedMatch;
    }

    /**
     * Start the next game in a match.
     *
     * @param match the match entity (must already be persisted)
     * @return a {@link GameStartRecord} with the created game and selected question
     * @throws IllegalStateException if match is not in progress or no question available
     */
    @Transactional
    public GameStartRecord startNextGame(Match match) {
        return startNextGame(match, 501);
    }

    /** Variant of {@link #startNextGame} that uses a football filter to resolve the question. */
    @Transactional
    public GameStartRecord startNextGameWithFilter(Match match, int startingScore, FootballFilter filter) {
        log.debug("Starting game for match {} with football filter (startingScore={})", match.getId(), startingScore);

        if (match.getStatus() != Match.MatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("Match is not in progress");
        }

        Question question = questionService.selectQuestionByFilter(filter)
            .orElseThrow(() -> new IllegalStateException(
                "No football question available for filter: scope=" + filter.getScope()
                + ", league=" + filter.getLeague() + ", club=" + filter.getClub()
                + ", stat=" + filter.getStatType()));

        long completedGames = gameRepository.countByMatchIdAndStatus(match.getId(), Game.GameStatus.COMPLETED);
        int gameNumber = (int) completedGames + 1;

        Game game = gameService.createGame(match.getId(), question.getId(), gameNumber, startingScore);

        log.info("Game started with filter: matchId={}, gameNumber={}, questionId={}, startingScore={}",
            match.getId(), gameNumber, question.getId(), startingScore);

        return new GameStartRecord(game, question);
    }

    @Transactional
    public GameStartRecord startNextGame(Match match, int startingScore) {
        log.debug("Starting next game for match {} (startingScore={})", match.getId(), startingScore);

        // Validate match is in progress
        if (match.getStatus() != Match.MatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("Match is not in progress");
        }

        // Select random question with match difficulty
        Optional<Question> questionOpt = questionService.selectRandomQuestion(
            match.getCategoryId(),
            match.getDifficulty(),
            10 // DEFAULT_MIN_ANSWERS
        );
        if (questionOpt.isEmpty()) {
            throw new IllegalStateException("No question available for category");
        }

        Question question = questionOpt.get();

        // Determine game number (number of completed games + 1)
        long completedGames = gameRepository.countByMatchIdAndStatus(match.getId(), Game.GameStatus.COMPLETED);
        int gameNumber = (int) completedGames + 1;

        // Create game
        Game game = gameService.createGame(match.getId(), question.getId(), gameNumber, startingScore);

        log.info("Game started: matchId={}, gameNumber={}, questionId={}, startingScore={}",
            match.getId(), gameNumber, question.getId(), startingScore);

        return new GameStartRecord(game, question);
    }

    /**
     * Handle game completion - update match state and check for match winner.
     *
     * @param completedGame the completed game
     */
    @Transactional
    public void handleGameCompletion(Game completedGame) {
        log.debug("Handling game completion: gameId={}, winner={}",
            completedGame.getId(), completedGame.getWinnerId());

        Match match = getMatchOrThrow(completedGame.getMatchId());

        // Increment win count for winner (winnerId is null for solo forfeits)
        if (completedGame.getWinnerId() == null) {
            // Solo forfeit — no winner means the player gave up. Mark the match
            // as ABANDONED (not COMPLETED) since there is no opponent to declare.
            match.setStatus(Match.MatchStatus.ABANDONED);
            match.setCompletedAt(java.time.LocalDateTime.now());
            matchRepository.save(match);
            log.info("Match abandoned (solo forfeit): matchId={}, gameId={}", match.getId(), completedGame.getId());
            return;
        } else if (completedGame.getWinnerId().equals(match.getPlayer1Id())) {
            match.setPlayer1GamesWon(match.getPlayer1GamesWon() + 1);
        }

        // Check if match is complete
        if (isMatchComplete(match)) {
            completeMatch(match);
        }

        matchRepository.save(match);
        log.info("Match updated: matchId={}, player1GamesWon={}, status={}",
            match.getId(), match.getPlayer1GamesWon(), match.getStatus());
    }

    /**
     * Check if a match is complete (one player has reached required wins).
     *
     * @param match the match
     * @return true if match is complete
     */
    public boolean isMatchComplete(Match match) {
        return match.getPlayer1GamesWon() >= match.getFormat().getGamesToWin();
    }

    /**
     * Get active matches for a player.
     *
     * @param playerId the player UUID
     * @return list of active matches
     */
    @Transactional(readOnly = true)
    public List<Match> getActiveMatchesForPlayer(UUID playerId) {
        return matchRepository.findActiveMatchesByPlayerId(playerId);
    }

    /**
     * Get match by ID.
     *
     * @param matchId the match UUID
     * @return optional match
     */
    @Transactional(readOnly = true)
    public Optional<Match> getMatchById(UUID matchId) {
        return matchRepository.findById(matchId);
    }

    /**
     * Get all games for a match.
     *
     * @param matchId the match UUID
     * @return list of games ordered by game number
     */
    @Transactional(readOnly = true)
    public List<Game> getGamesForMatch(UUID matchId) {
        return gameRepository.findByMatchIdOrderByGameNumberAsc(matchId);
    }

    /**
     * Get current active game for a match.
     *
     * @param matchId the match UUID
     * @return optional active game
     */
    @Transactional(readOnly = true)
    public Optional<Game> getCurrentGame(UUID matchId) {
        return gameRepository.findByMatchIdAndStatus(matchId, Game.GameStatus.IN_PROGRESS);
    }

    /**
     * Get match statistics for a player.
     *
     * @param playerId the player UUID
     * @return match statistics
     */
    @Transactional(readOnly = true)
    public MatchStats getMatchStats(UUID playerId) {
        long wins = matchRepository.countWinsByPlayerId(playerId);
        long losses = matchRepository.countLossesByPlayerId(playerId);
        return new MatchStats(wins, losses);
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    private Match getMatchOrThrow(UUID matchId) {
        return matchRepository.findById(matchId)
            .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));
    }

    private void completeMatch(Match match) {
        match.setStatus(Match.MatchStatus.COMPLETED);
        match.setWinnerId(match.getPlayer1Id());

        log.info("Match completed: matchId={}, winner={}, player1GamesWon={}",
            match.getId(), match.getWinnerId(), match.getPlayer1GamesWon());
    }

    /**
     * Handle a game-completion event published by {@link GameService}.
     * Replaces the former direct {@code matchService.handleGameCompletion()}
     * call that created a circular dependency.
     */
    @EventListener
    @Transactional
    public void onGameCompleted(GameCompletedEvent event) {
        Game game = gameService.getGameById(event.gameId())
                .orElseThrow(() -> new IllegalArgumentException("Game not found: " + event.gameId()));
        handleGameCompletion(game);
    }

    /**
     * Simple record for match statistics.
     */
    public record MatchStats(long wins, long losses) {
        public long total() {
            return wins + losses;
        }
    }
}
