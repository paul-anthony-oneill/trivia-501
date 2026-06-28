package com.trivia501.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Development-mode authentication filter.
 *
 * @deprecated Replaced by {@link OptionalJwtFilter} which provides hybrid
 *             JWT + anonymous authentication on every profile with no
 *             dev/prod split.  Kept on disk as reference documentation
 *             and for the {@link #DEV_PLAYER_ID} constant still referenced
 *             by older test classes.
 */
@Deprecated
@Slf4j
public class DevModeAuthFilter extends OncePerRequestFilter {

    /**
     * Fixed player UUID injected by this filter.
     * Use this constant in tests that assert on player-scoped behaviour
     * (e.g. game ownership checks) so the value stays in sync with what
     * the filter actually injects.
     */
    public static final String DEV_PLAYER_ID = "00000000-0000-0000-0000-000000000001";

    private static final List<SimpleGrantedAuthority> DEV_AUTHORITIES = List.of(
        new SimpleGrantedAuthority("ROLE_USER"),
        new SimpleGrantedAuthority("ROLE_ADMIN")
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Don't override real authentication already set by another filter or test harness;
        // but DO replace the anonymous token that AnonymousAuthenticationFilter injects.
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(DEV_PLAYER_ID, null, DEV_AUTHORITIES);
            SecurityContextHolder.getContext().setAuthentication(token);
            // ponytail: mark request as authenticated so RateLimitFilter gives 100/min, not 10/min
            request.setAttribute(OptionalJwtFilter.AUTH_TYPE_ATTR, OptionalJwtFilter.AUTH_TYPE_JWT);
            log.trace("DevModeAuthFilter: injected dev principal for {}", request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }
}
