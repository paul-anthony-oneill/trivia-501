package com.trivia501.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a single game within a match.
 * Each game starts at 501 points and ends when a player reaches 0 (or within -10 to 0 range).
 */
@Entity
@Table(name = "games", indexes = {
    @Index(name = "idx_games_match", columnList = "match_id"),
    @Index(name = "idx_games_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "game_number", nullable = false)
    private Integer gameNumber;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private GameStatus status = GameStatus.IN_PROGRESS;

    @Column(name = "current_turn_player_id")
    private UUID currentTurnPlayerId;

    @Column(name = "player1_score", nullable = false)
    @Builder.Default
    private Integer player1Score = 501;

    @Column(name = "player2_score", nullable = false)
    @Builder.Default
    private Integer player2Score = 501;

    @Column(name = "player1_consecutive_timeouts")
    @Builder.Default
    private Integer player1ConsecutiveTimeouts = 0;

    @Column(name = "player2_consecutive_timeouts")
    @Builder.Default
    private Integer player2ConsecutiveTimeouts = 0;

    @Column(name = "winner_id")
    private UUID winnerId;

    @Column(name = "turn_count")
    @Builder.Default
    private Integer turnCount = 0;

    @Column(name = "turn_timer_seconds")
    @Builder.Default
    private Integer turnTimerSeconds = 45;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // Signed by go-signer on checkout. Null for games before the signing service existed,
    // or when the service was unavailable. Absence means unverified, not invalid.
    @Column(name = "result_token", columnDefinition = "TEXT")
    private String resultToken;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        // createdAt / updatedAt are set by @CreatedDate / @LastModifiedDate.
        // startedAt is a business timestamp — default to now on first persist.
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }

    public enum GameStatus {
        WAITING,        // Waiting to start
        IN_PROGRESS,
        COMPLETED,
        ABANDONED
    }

    /**
     * Check if score is in valid checkout range (-10 to 0 inclusive).
     */
    public boolean isCheckoutScore(int score) {
        return score >= -10 && score <= 0;
    }

    /**
     * Get current score for specified player.
     */
    public Integer getScoreForPlayer(UUID playerId) {
        if (playerId.equals(getPlayerIdFromMatch(1))) {
            return player1Score;
        } else if (playerId.equals(getPlayerIdFromMatch(2))) {
            return player2Score;
        }
        return null;
    }

    /**
     * Helper to get player ID based on player number (1 or 2).
     * Note: This requires the match context, so it's a placeholder.
     * In actual usage, you'd pass player IDs or use match entity.
     */
    private UUID getPlayerIdFromMatch(int playerNumber) {
        // This is a placeholder - actual implementation would need Match reference
        // or pass player IDs directly in the service layer
        return null;
    }
}
