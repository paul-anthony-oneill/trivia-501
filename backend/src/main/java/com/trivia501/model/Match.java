package com.trivia501.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a single-player match. Multiplayer is deferred indefinitely;
 * when it returns, the schema and engine will be rebuilt from a real product spec.
 */
@Entity
@Table(name = "matches", indexes = {
    @Index(name = "idx_matches_status", columnList = "status"),
    @Index(name = "idx_matches_player1", columnList = "player1_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "player1_id")
    private UUID player1Id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MatchType type = MatchType.CASUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MatchFormat format = MatchFormat.BEST_OF_1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MatchStatus status = MatchStatus.IN_PROGRESS;

    @Column(name = "winner_id")
    private UUID winnerId;

    @Column(name = "player1_games_won")
    @Builder.Default
    private Integer player1GamesWon = 0;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "difficulty")
    @Builder.Default
    private Integer difficulty = 2;

    @Column(name = "game_mode", nullable = false)
    @Builder.Default
    private String gameMode = "STANDARD";

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

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

    public enum MatchType {
        CASUAL,
        RANKED,
        DAILY_CHALLENGE
    }

    public enum MatchFormat {
        BEST_OF_1(1);

        private final int gamesToWin;

        MatchFormat(int gamesToWin) {
            this.gamesToWin = gamesToWin;
        }

        public int getGamesToWin() {
            return gamesToWin;
        }
    }

    public enum MatchStatus {
        IN_PROGRESS,
        COMPLETED,
        ABANDONED
    }
}
