package com.trivia501.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Single source of truth for the application clock.
 * <p>
 * All date/time logic must use this bean so that date-boundary decisions
 * (daily challenge date, stale-game cutoff, scheduler windows) are consistent
 * regardless of the JVM's default timezone. In production on Fly.io the JVM
 * default is typically UTC, but local dev (PDT etc.) shifts day boundaries by
 * up to 16 hours relative to the client's UTC-based daily lock.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
