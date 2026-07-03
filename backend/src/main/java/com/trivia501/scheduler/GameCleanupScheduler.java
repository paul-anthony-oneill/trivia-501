package com.trivia501.scheduler;

import com.trivia501.model.Game;
import com.trivia501.repository.GameRepository;
import com.trivia501.service.GameService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodic cleanup of stale in-progress games.
 *
 * <p>Two independent sweeps:
 * <ol>
 *   <li><strong>Free Play / casual:</strong> abandons non-daily games idle for
 *       {@link #FREE_PLAY_STALE_MINUTES} minutes. An orphaned free-play game
 *       from a closed tab shouldn't sit IN_PROGRESS forever.</li>
 *   <li><strong>Daily Challenge:</strong> abandons daily games whose
 *       {@code created_at} is before the start of today. Today's dailies are
 *       <em>never</em> abandoned — the mode is explicitly untimed
 *       ("play at your own pace").</li>
 * </ol>
 */
@Component
@Slf4j
public class GameCleanupScheduler {

    /** Free-play / casual games idle for this many minutes are abandoned. */
    private static final int FREE_PLAY_STALE_MINUTES = 15;

    private final GameRepository gameRepository;
    private final GameService gameService;
    private final Clock clock;

    public GameCleanupScheduler(GameRepository gameRepository, GameService gameService, Clock clock) {
        this.gameRepository = gameRepository;
        this.gameService = gameService;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanupStaleGames() {
        // ── Sweep 1: Free Play / casual (idle timer) ─────────────────────────
        LocalDateTime freePlayCutoff = LocalDateTime.now(clock).minusMinutes(FREE_PLAY_STALE_MINUTES);
        List<Game> staleFreePlay = gameRepository.findStaleGames(freePlayCutoff);

        if (!staleFreePlay.isEmpty()) {
            log.info("Stale-game cleanup: found {} free-play games with no activity since {}",
                    staleFreePlay.size(), freePlayCutoff);
            gameService.abandonStaleGames(staleFreePlay);
        }

        // ── Sweep 2: Daily Challenge (previous-day sweep only) ───────────────
        LocalDateTime startOfToday = LocalDate.now(clock).atStartOfDay();
        List<Game> staleDailies = gameRepository.findStaleDailyGames(startOfToday);

        if (!staleDailies.isEmpty()) {
            log.info("Stale-game cleanup: found {} daily-challenge games from before today",
                    staleDailies.size());
            gameService.abandonStaleGames(staleDailies);
        }
    }
}
