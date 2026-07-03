package com.trivia501.repository;

import com.trivia501.model.Game;
import com.trivia501.model.Game.GameStatus;
import com.trivia501.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Game entity.
 */
@Repository
public interface GameRepository extends JpaRepository<Game, UUID> {

    /**
     * Find all games for a match, ordered by game number.
     *
     * @param matchId the match UUID
     * @return list of games
     */
    List<Game> findByMatchIdOrderByGameNumberAsc(UUID matchId);

    /**
     * Find current active game for a match.
     *
     * @param matchId the match UUID
     * @return optional active game
     */
    Optional<Game> findByMatchIdAndStatus(UUID matchId, GameStatus status);

    /**
     * Find the latest game in a match.
     *
     * @param matchId the match UUID
     * @return optional game
     */
    @Query("""
        SELECT g FROM Game g
        WHERE g.matchId = :matchId
        ORDER BY g.gameNumber DESC
        LIMIT 1
        """)
    Optional<Game> findLatestGameByMatchId(@Param("matchId") UUID matchId);

    /**
     * Count completed games in a match.
     *
     * @param matchId the match UUID
     * @return number of completed games
     */
    long countByMatchIdAndStatus(UUID matchId, GameStatus status);

    /**
     * Count games won by a specific player in a match.
     *
     * @param matchId the match UUID
     * @param playerId the player UUID
     * @return number of games won
     */
    long countByMatchIdAndWinnerId(UUID matchId, UUID playerId);

    /**
     * Find all completed games for a match.
     *
     * @param matchId the match UUID
     * @return list of completed games
     */
    List<Game> findByMatchIdAndStatusOrderByGameNumberAsc(UUID matchId, GameStatus status);

    /**
     * Find the most recent in-progress game for a player (across all their matches).
     */
    @Query("""
        SELECT g FROM Game g
        JOIN Match m ON g.matchId = m.id
        WHERE m.player1Id = :playerId
          AND g.status = 'IN_PROGRESS'
        ORDER BY g.updatedAt DESC
        LIMIT 1
        """)
    Optional<Game> findActiveGameByPlayerId(@Param("playerId") UUID playerId);

    /**
     * Find all in-progress non-daily games last updated before the given cutoff.
     * Daily-challenge games are untimed and swept separately (after their day ends).
     */
    @Query("""
        SELECT g FROM Game g
        JOIN Match m ON g.matchId = m.id
        WHERE g.status = 'IN_PROGRESS'
          AND g.updatedAt < :cutoff
          AND m.type <> 'DAILY_CHALLENGE'
        """)
    List<Game> findStaleGames(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Find in-progress daily-challenge games created before the given cutoff.
     * Only used to sweep dailies from previous days (cutoff = start of today).
     * Today's dailies are never abandoned by the cleanup scheduler.
     */
    @Query("""
        SELECT g FROM Game g
        JOIN Match m ON g.matchId = m.id
        WHERE g.status = 'IN_PROGRESS'
          AND m.type = 'DAILY_CHALLENGE'
          AND g.createdAt < :cutoff
        """)
    List<Game> findStaleDailyGames(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Find all in-progress games for a player (across all their matches).
     */
    @Query("""
        SELECT g FROM Game g
        JOIN Match m ON g.matchId = m.id
        WHERE m.player1Id = :playerId
          AND g.status = 'IN_PROGRESS'
        """)
    List<Game> findActiveGamesByPlayerId(@Param("playerId") UUID playerId);

    /**
     * Find in-progress games for a player that are safe to abandon when starting
     * a new game. Excludes today's daily-challenge games so starting a new daily
     * or free-play game doesn't wipe another category's daily progress.
     */
    @Query("""
        SELECT g FROM Game g
        JOIN Match m ON g.matchId = m.id
        WHERE m.player1Id = :playerId
          AND g.status = 'IN_PROGRESS'
          AND NOT (m.type = 'DAILY_CHALLENGE' AND g.createdAt >= :startOfToday)
        """)
    List<Game> findAbandonableGamesByPlayerId(
        @Param("playerId") UUID playerId,
        @Param("startOfToday") LocalDateTime startOfToday
    );

    /**
     * Find a daily challenge game for a player in a specific category with the given status,
     * created on or after startOfDay and before endOfDay. Used to enforce the
     * one-attempt-per-day rule and to resume in-progress daily games.
     */
    @Query("""
        SELECT g FROM Game g
        JOIN Match m ON g.matchId = m.id
        WHERE m.player1Id = :playerId
          AND m.type = :matchType
          AND m.categoryId = :categoryId
          AND g.status = :status
          AND g.createdAt >= :startOfDay
          AND g.createdAt < :endOfDay
        ORDER BY g.updatedAt DESC
        LIMIT 1
        """)
    Optional<Game> findDailyGameByPlayerCategoryAndStatus(
        @Param("playerId") UUID playerId,
        @Param("categoryId") UUID categoryId,
        @Param("matchType") Match.MatchType matchType,
        @Param("status") GameStatus status,
        @Param("startOfDay") LocalDateTime startOfDay,
        @Param("endOfDay") LocalDateTime endOfDay
    );
}
