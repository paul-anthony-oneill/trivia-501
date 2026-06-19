package com.trivia501.repository;

import com.trivia501.model.Match;
import com.trivia501.model.Match.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Match entity.
 */
@Repository
public interface MatchRepository extends JpaRepository<Match, UUID> {

    /**
     * Find matches for a specific player.
     *
     * @param playerId the player UUID
     * @return list of matches
     */
    @Query("""
        SELECT m FROM Match m
        WHERE m.player1Id = :playerId
        ORDER BY m.startedAt DESC
        """)
    List<Match> findByPlayerId(@Param("playerId") UUID playerId);

    /**
     * Find active matches for a player.
     *
     * @param playerId the player UUID
     * @return list of active matches
     */
    @Query("""
        SELECT m FROM Match m
        WHERE m.player1Id = :playerId
          AND m.status = 'IN_PROGRESS'
        """)
    List<Match> findActiveMatchesByPlayerId(@Param("playerId") UUID playerId);

    /**
     * Find matches by status.
     *
     * @param status the match status
     * @return list of matches
     */
    List<Match> findByStatus(MatchStatus status);

    /**
     * Find recent matches for a player (last N days).
     *
     * @param playerId the player UUID
     * @param since the start date
     * @return list of matches
     */
    @Query("""
        SELECT m FROM Match m
        WHERE m.player1Id = :playerId
          AND m.startedAt >= :since
        ORDER BY m.startedAt DESC
        """)
    List<Match> findRecentByPlayerId(
        @Param("playerId") UUID playerId,
        @Param("since") LocalDateTime since
    );

    /**
     * Count wins for a player.
     *
     * @param playerId the player UUID
     * @return number of wins
     */
    @Query("""
        SELECT COUNT(m) FROM Match m
        WHERE m.winnerId = :playerId
        """)
    long countWinsByPlayerId(@Param("playerId") UUID playerId);

    /**
     * Count losses for a player.
     *
     * @param playerId the player UUID
     * @return number of losses
     */
    @Query("""
        SELECT COUNT(m) FROM Match m
        WHERE m.player1Id = :playerId
          AND m.winnerId IS NOT NULL
          AND m.winnerId != :playerId
          AND m.status = 'COMPLETED'
        """)
    long countLossesByPlayerId(@Param("playerId") UUID playerId);
}
