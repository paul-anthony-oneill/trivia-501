package com.trivia501.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Arrays;

/**
 * Spring Security configuration for Trivia 501.
 *
 * <h3>Design intent</h3>
 * <p>{@link OptionalJwtFilter} runs on every request — no dev/prod split.
 * If a valid Supabase JWT is present the user is authenticated with their real
 * identity; otherwise an anonymous session UUID is assigned via cookie.
 * Gameplay works identically for both paths.
 *
 * <h3>Endpoint access policy</h3>
 * <pre>
 *   /api/admin/**           → ROLE_ADMIN   (also @PreAuthorize at method level)
 *   /api/freeplay/**        → ROLE_USER / ROLE_ADMIN
 *   GET /api/daily-challenge/** → permitAll (public browsing of daily challenges)
 *   POST /api/daily-challenge/** → authenticated (game start, submit, abandon)
 *   /api/entities/**        → permitAll    (public autocomplete)
 *   /api/categories/**      → permitAll    (public category listing)
 *   /actuator/health        → permitAll    (liveness probe)
 *   everything else         → authenticated
 * </pre>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final OptionalJwtFilter optionalJwtFilter;
    private final java.util.Optional<RateLimitFilter> rateLimitFilter;
    private final boolean testProfile;

    public SecurityConfig(JwtDecoder jwtDecoder,
                          JwtAuthenticationConverter jwtConverter,
                          java.util.Optional<RateLimitFilter> rateLimitFilter,
                          Environment environment,
                          @Value("${SUPABASE_JWT_SECRET:#{null}}") String jwtSecret) {
        boolean jwtConfigured = jwtSecret != null && !jwtSecret.isEmpty();
        this.optionalJwtFilter = new OptionalJwtFilter(jwtDecoder, jwtConverter, jwtConfigured);
        this.rateLimitFilter = rateLimitFilter;
        // DevModeAuthFilter on every non-prod profile (default, dev, test).
        // Only OptionalJwtFilter when SPRING_PROFILES_ACTIVE includes "prod".
        var profiles = Arrays.asList(environment.getActiveProfiles());
        this.testProfile = !profiles.contains("prod");
        log.info("SecurityConfig: auth filter = {} (JWT {}configured)",
                testProfile ? "DevModeAuthFilter" : "OptionalJwtFilter",
                jwtConfigured ? "" : "NOT ");
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())

            // CSRF disabled — this is a stateless REST API with Bearer-token auth.
            // However, OptionalJwtFilter sets a cookie (X-Anonymous-Id) for anonymous
            // sessions. If real auth tokens are ever stored in cookies instead of
            // Bearer headers, CSRF protection must be re-enabled before shipping.
            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/entities/**").permitAll()
                .requestMatchers("/api/categories/**").permitAll()
                .requestMatchers("/api/football/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/daily-challenge/games/*/answers").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/daily-challenge/**").permitAll()
                .requestMatchers("/api/daily-challenge/**").authenticated()
                .requestMatchers("/api/freeplay/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            );

        // Auth filter: DevModeAuthFilter in test profile (grants ROLE_USER+ROLE_ADMIN to all
        // requests); OptionalJwtFilter on every other profile (JWT + anonymous cookie fallback).
        if (testProfile) {
            http.addFilterBefore(new DevModeAuthFilter(),
                org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        } else {
            http.addFilterBefore(optionalJwtFilter,
                org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        }

        // Rate limiting — only active outside the test profile (@Profile("!test") on RateLimitFilter)
        rateLimitFilter.ifPresent(f -> http.addFilterBefore(f,
            org.springframework.security.web.access.intercept.AuthorizationFilter.class));

        return http.build();
    }

}
