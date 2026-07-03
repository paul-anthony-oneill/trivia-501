package com.trivia501.service;

import com.trivia501.model.PlayerProfile;
import com.trivia501.repository.PlayerProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages {@link PlayerProfile} records for authenticated users only.
 * Anonymous players (cookie-based session UUIDs) are silently skipped —
 * their games are not persisted beyond the session.
 */
@Service
@Slf4j
public class PlayerProfileService {

    private final PlayerProfileRepository repository;

    public PlayerProfileService(PlayerProfileRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns {@code true} if the current request has a real JWT
     * (not an anonymous session cookie).
     */
    public static boolean isAuthenticated() {
        return SecurityContextHolder.getContext().getAuthentication()
            instanceof JwtAuthenticationToken;
    }

    /**
     * Upserts a profile for the given player, extracting display name and
     * avatar from the JWT {@code user_metadata} claim when available.
     * No-op for anonymous sessions.
     */
    @Transactional
    public Optional<PlayerProfile> ensureProfile(UUID playerId) {
        if (!isAuthenticated()) {
            return Optional.empty();
        }

        var auth = (JwtAuthenticationToken)
            SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = auth.getToken();
        Map<String, Object> meta = jwt.getClaimAsMap("user_metadata");
        String displayName = meta != null ? (String) meta.get("full_name") : null;
        String avatarUrl = meta != null ? (String) meta.get("avatar_url") : null;

        return Optional.of(repository.findByPlayerId(playerId)
            .map(profile -> {
                profile.setLastActiveAt(LocalDateTime.now());
                if (displayName != null) profile.setDisplayName(displayName);
                if (avatarUrl != null) profile.setAvatarUrl(avatarUrl);
                return repository.save(profile);
            })
            .orElseGet(() -> {
                var profile = PlayerProfile.builder()
                    .playerId(playerId)
                    .displayName(displayName)
                    .avatarUrl(avatarUrl)
                    .build();
                log.info("Created player profile for {}", playerId);
                return repository.save(profile);
            }));
    }

    /**
     * Increments game counters after a completed game.
     * No-op for anonymous sessions.
     */
    @Transactional
    public void recordGameCompleted(UUID playerId, int finalScore, boolean isWin) {
        if (!isAuthenticated()) {
            return;
        }
        repository.findByPlayerId(playerId).ifPresent(profile -> {
            profile.setGamesPlayed(profile.getGamesPlayed() + 1);
            if (isWin) profile.setGamesWon(profile.getGamesWon() + 1);
            profile.setTotalScore(profile.getTotalScore() + finalScore);
            // "Best" = closest to zero. abs(0) < abs(-10), so a perfect checkout
            // edges out a -10 finish; abs(3) < abs(-5), so 3 beats 5, etc.
            if (profile.getBestScore() == null
                    || Math.abs(finalScore) < Math.abs(profile.getBestScore())) {
                profile.setBestScore(finalScore);
            }
            profile.setLastActiveAt(LocalDateTime.now());
            repository.save(profile);
        });
    }

    public Optional<PlayerProfile> findByPlayerId(UUID playerId) {
        return repository.findByPlayerId(playerId);
    }
}
