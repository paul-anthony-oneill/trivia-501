package com.trivia501.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    @Min(1) @Max(3)
    private Integer difficulty;

    @Min(value = 2, message = "startingScore must be at least 2")
    @Max(value = 501, message = "startingScore cannot exceed 501")
    private Integer startingScore;

    /** Optional football-specific question filter. When present, overrides random question selection. */
    private FootballFilter footballFilter;

    /** Optional explicit question ID for deterministic game start. Skips random selection when set. */
    private UUID questionId;
}
