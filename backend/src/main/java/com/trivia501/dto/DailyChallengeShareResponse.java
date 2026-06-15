package com.trivia501.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyChallengeShareResponse {

    private UUID gameId;
    private String categoryName;
    private String categorySlug;
    private LocalDate challengeDate;
    private int startingScore;
    private int finalScore;
    private int turnCount;
    private boolean isWin;
    private List<MoveEmoji> moveEmojis;
    // Null when go-signer was unavailable at checkout. Presence means the result is
    // cryptographically verifiable against the public key at GET /pubkey on go-signer.
    private String resultToken;

    public enum MoveEmoji {
        VALID,      // 🟩
        BUST,       // 🟥
        INVALID,    // ⬜
        CHECKOUT    // 🎯
    }
}
