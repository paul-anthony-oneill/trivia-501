package com.trivia501.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for game state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameStateResponse {

    /**
     * Game ID.
     */
    private UUID gameId;

    /**
     * Match ID.
     */
    private UUID matchId;

    /**
     * Question ID.
     */
    private UUID questionId;

    /**
     * The question text.
     */
    private String questionText;

    /**
     * Current score (starts at 501).
     */
    private Integer currentScore;

    /**
     * Number of turns taken.
     */
    private Integer turnCount;

    /**
     * Game status (IN_PROGRESS, COMPLETED).
     */
    private String status;

    /**
     * Whether the player won (reached 0 or checkout range).
     */
    private Boolean isWin;

    /**
     * Entity type that scopes the autocomplete dropdown for this question
     * (e.g. "footballer", "city", "country").  Sourced from the question's
     * {@code config.entity_type} JSONB field.  Defaults to "footballer" if
     * the question config does not specify one.
     */
    private String entityType;

    /**
     * In-game hint statistics about the remaining answer pool.
     *
     * <ul>
     *   <li>While score {@code > 180}: surface {@code maxScoresLeft} — how many
     *       unused answers are worth exactly 180 points.</li>
     *   <li>While score {@code ≤ 180}: surface {@code checkoutsLeft} — how many
     *       unused answers would win the game in a single move.</li>
     * </ul>
     */
    private GameHints hints;

    /**
     * Move history for this game (newest first). Null when moves are not requested.
     */
    private List<MoveDto> moves;
}
