package com.trivia501.scheduler;

import com.trivia501.model.Game;
import com.trivia501.repository.GameRepository;
import com.trivia501.service.GameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GameCleanupScheduler Tests")
class GameCleanupSchedulerTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameService gameService;

    // 2026-07-03T12:00:00Z — midday UTC, well after the 00:00 daily boundary
    private static final Instant FIXED_NOW = Instant.parse("2026-07-03T12:00:00Z");
    private static final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private GameCleanupScheduler scheduler;

    private Game freePlayGame;
    private Game dailyGame;

    @BeforeEach
    void setUp() {
        scheduler = new GameCleanupScheduler(gameRepository, gameService, clock);

        freePlayGame = Game.builder()
            .id(UUID.randomUUID())
            .status(Game.GameStatus.IN_PROGRESS)
            .build();

        dailyGame = Game.builder()
            .id(UUID.randomUUID())
            .status(Game.GameStatus.IN_PROGRESS)
            .build();
    }

    @Test
    @DisplayName("Daily challenge games created today are NOT abandoned")
    void shouldNotAbandonTodaysDailyGames() {
        // findStaleDailyGames is called with startOfToday (2026-07-03T00:00:00Z).
        // It returns games with created_at < startOfToday — i.e. previous-day games.
        // Today's dailies have created_at >= startOfToday, so the repo returns empty.
        when(gameRepository.findStaleGames(any())).thenReturn(List.of());
        when(gameRepository.findStaleDailyGames(any())).thenReturn(List.of());

        scheduler.cleanupStaleGames();

        // Today's daily games are not in the stale list → never abandoned
        verify(gameService, never()).abandonStaleGames(any());
    }

    @Test
    @DisplayName("Daily challenge games from before today ARE abandoned")
    void shouldAbandonPreviousDayDailyGames() {
        // Simulate: a daily game from yesterday is returned by findStaleDailyGames
        when(gameRepository.findStaleGames(any())).thenReturn(List.of());
        when(gameRepository.findStaleDailyGames(any())).thenReturn(List.of(dailyGame));

        scheduler.cleanupStaleGames();

        verify(gameService).abandonStaleGames(List.of(dailyGame));

        // Verify the cutoff passed to findStaleDailyGames is start of today (midnight UTC)
        ArgumentCaptor<LocalDateTime> dailyCutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(gameRepository).findStaleDailyGames(dailyCutoffCaptor.capture());
        assertThat(dailyCutoffCaptor.getValue())
            .isEqualTo(LocalDateTime.of(2026, 7, 3, 0, 0));
    }

    @Test
    @DisplayName("Free-play games idle past 15-minute cutoff ARE abandoned")
    void shouldAbandonStaleFreePlayGames() {
        when(gameRepository.findStaleGames(any())).thenReturn(List.of(freePlayGame));
        when(gameRepository.findStaleDailyGames(any())).thenReturn(List.of());

        scheduler.cleanupStaleGames();

        verify(gameService).abandonStaleGames(List.of(freePlayGame));

        // Verify the cutoff passed to findStaleGames is 15 minutes before "now"
        ArgumentCaptor<LocalDateTime> freePlayCutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(gameRepository).findStaleGames(freePlayCutoffCaptor.capture());
        assertThat(freePlayCutoffCaptor.getValue())
            .isEqualTo(LocalDateTime.of(2026, 7, 3, 11, 45));
    }

    @Test
    @DisplayName("Both sweeps can find stale games in the same run")
    void shouldAbandonBothStaleFreePlayAndPreviousDayDailies() {
        when(gameRepository.findStaleGames(any())).thenReturn(List.of(freePlayGame));
        when(gameRepository.findStaleDailyGames(any())).thenReturn(List.of(dailyGame));

        scheduler.cleanupStaleGames();

        verify(gameService).abandonStaleGames(List.of(freePlayGame));
        verify(gameService).abandonStaleGames(List.of(dailyGame));
    }
}
