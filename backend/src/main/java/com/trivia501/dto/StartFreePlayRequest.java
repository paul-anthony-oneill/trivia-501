package com.trivia501.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for starting a Free Play (single-player) game.
 *
 * <p>Player identity is never supplied by the client — it is derived from
 * the authenticated {@link java.security.Principal} in the controller.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartFreePlayRequest {

    private String categorySlug;

    private Integer difficulty;

    private Integer startingScore;

    /** Optional football-specific question filter. When present, overrides random question selection. */
    private FootballFilter footballFilter;

    /** Optional explicit question ID for deterministic game start. Skips random selection when set. */
    private UUID questionId;
}
