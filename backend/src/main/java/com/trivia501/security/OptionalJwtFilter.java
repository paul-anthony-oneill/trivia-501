package com.trivia501.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.AnonymousAuthenticationToken;

/**
 * Hybrid authentication filter for Trivia 501.
 *
 * <p>Runs on <strong>every</strong> profile — there is no dev/prod split.
 * Two code paths, chosen per-request:
 *
 * <ol>
 *   <li><strong>JWT present:</strong> validates the Supabase-issued Bearer token,
 *       converts claims to a {@link JwtAuthenticationToken}, and sets it in the
 *       security context.  The {@code sub} claim (Supabase user UUID) becomes
 *       {@code principal.getName()} so controllers see the real player identity.
 *       Supabase anonymous JWTs ({@code role: "anonymous"}) are rejected and
 *       fall back to cookie auth — anonymous sign-in is intentionally disabled.</li>
 *   <li><strong>No JWT:</strong> reads or creates an anonymous UUID from the
 *       {@code X-Anonymous-Id} cookie, creates a
 *       {@link UsernamePasswordAuthenticationToken} with {@code ROLE_USER}.
 *       Gameplay works identically for anonymous players; only persistence
 *       features (profiles, history) are skipped.</li>
 * </ol>
 *
 * <h3>Cookie rotation</h3>
 * <p>Controllers signal that the anonymous session should be rotated by setting
 * the {@code X-Rotate-Anonymous-Id} request attribute to {@code "true"}.
 * After the filter chain completes, this filter checks for the attribute and
 * issues a new cookie with a fresh UUID. Controllers should do this on game
 * start and game completion to prevent cross-game tracking and limit the window
 * for cookie exfiltration replay.
 *
 * <p>Downstream code can distinguish the two paths by checking whether the
 * {@code Authentication} is a {@link JwtAuthenticationToken} (real user) or a
 * {@link UsernamePasswordAuthenticationToken} (anonymous session).
 */
@Slf4j
public class OptionalJwtFilter extends OncePerRequestFilter {

    /** Request attribute set to "jwt" or "anonymous" so downstream filters
     *  (e.g. {@link RateLimitFilter}) can distinguish auth type without
     *  importing Spring Security classes. */
    public static final String AUTH_TYPE_ATTR = "X-Auth-Type";
    public static final String AUTH_TYPE_JWT = "jwt";
    public static final String AUTH_TYPE_ANON = "anonymous";

    /** Request attribute controllers set to "true" to signal the filter
     *  should rotate the anonymous session cookie after the request completes.
     *  Use on game start and game completion to prevent cross-game tracking. */
    public static final String ROTATE_ANON_ATTR = "X-Rotate-Anonymous-Id";

    private static final String ANON_COOKIE = "X-Anonymous-Id";

    /** Sliding expiration for anonymous session cookies — 24 hours in seconds.
     *  Every authenticated request extends the cookie back to 24 hours, so
     *  regular players keep their session alive while abandoned sessions
     *  auto-expire after a day of inactivity. */
    private static final int ANON_COOKIE_MAX_AGE = 86400; // 24 hours

    private static final String BEARER_PREFIX = "Bearer ";

    private static final List<SimpleGrantedAuthority> ANON_AUTHORITIES = List.of(
        new SimpleGrantedAuthority("ROLE_USER")
    );

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtConverter;
    private final boolean jwtConfigured;

    public OptionalJwtFilter(JwtDecoder jwtDecoder,
                             JwtAuthenticationConverter jwtConverter,
                             boolean jwtConfigured) {
        this.jwtDecoder = jwtDecoder;
        this.jwtConverter = jwtConverter;
        this.jwtConfigured = jwtConfigured;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // If a real (non-anonymous) authentication is already set (e.g. @WithMockUser in tests),
        // leave it in place — this filter only supplies identity when none exists yet.
        var existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing != null && existing.isAuthenticated() && !(existing instanceof AnonymousAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            try {
                var jwt = jwtDecoder.decode(token);
                // Reject Supabase anonymous JWTs — anonymous sign-in is intentionally
                // disabled. These users fall back to cookie-based anonymous sessions.
                if ("anonymous".equals(jwt.getClaimAsString("role"))) {
                    log.debug("OptionalJwtFilter: rejecting anonymous JWT — using cookie auth");
                    setAnonymousAuth(request, response);
                } else {
                    var auth = jwtConverter.convert(jwt);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    request.setAttribute(AUTH_TYPE_ATTR, AUTH_TYPE_JWT);
                    log.trace("OptionalJwtFilter: JWT authenticated — sub={}", jwt.getSubject());
                }
            } catch (JwtException e) {
                if (jwtConfigured) {
                    log.debug("OptionalJwtFilter: invalid JWT (secret configured) — returning 401", e);
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.getWriter().write("{\"error\":\"Invalid or expired token\"}");
                    return;
                }
                log.debug("OptionalJwtFilter: invalid JWT (no secret) — falling back to anonymous", e);
                setAnonymousAuth(request, response);
            } catch (AuthenticationException e) {
                log.warn("OptionalJwtFilter: JWT converter failed", e);
                if (jwtConfigured) {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.getWriter().write("{\"error\":\"Invalid token claims\"}");
                    return;
                }
                setAnonymousAuth(request, response);
            }
        } else {
            setAnonymousAuth(request, response);
        }

        filterChain.doFilter(request, response);

        // After the filter chain completes, check if a downstream controller
        // requested anonymous session rotation (game start / game complete).
        if (AUTH_TYPE_ANON.equals(request.getAttribute(AUTH_TYPE_ATTR))
                && "true".equals(request.getAttribute(ROTATE_ANON_ATTR))) {
            String newId = UUID.randomUUID().toString();
            Cookie cookie = new Cookie(ANON_COOKIE, newId);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setSecure(request.isSecure());
            cookie.setAttribute("SameSite", "Lax");
            cookie.setMaxAge(ANON_COOKIE_MAX_AGE);
            response.addCookie(cookie);

            var auth = new UsernamePasswordAuthenticationToken(newId, null, ANON_AUTHORITIES);
            SecurityContextHolder.getContext().setAuthentication(auth);

            log.debug("OptionalJwtFilter: rotated anonymous session to {}", newId);
        }
    }

    private void setAnonymousAuth(HttpServletRequest request, HttpServletResponse response) {
        String anonymousId = getCookieValue(request, ANON_COOKIE);

        // Validate the cookie is a canonical UUID — regenerate on garbage to
        // prevent malformed values from causing UUID.fromString() failures in
        // downstream code (controllers, repositories).
        if (anonymousId != null && !isCanonicalUuid(anonymousId)) {
            log.debug("OptionalJwtFilter: malformed anonymous cookie — regenerating");
            anonymousId = null;
        }

        if (anonymousId == null) {
            anonymousId = UUID.randomUUID().toString();
            log.trace("OptionalJwtFilter: created anonymous session {}", anonymousId);
        }

        // Always re-issue the cookie with a fresh 24h MaxAge (sliding window).
        // If the player is active they keep their session; abandoned sessions
        // auto-expire after 24 hours of inactivity.
        Cookie cookie = new Cookie(ANON_COOKIE, anonymousId);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setAttribute("SameSite", "Lax");
        cookie.setMaxAge(ANON_COOKIE_MAX_AGE);
        response.addCookie(cookie);

        var auth = new UsernamePasswordAuthenticationToken(anonymousId, null, ANON_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.setAttribute(AUTH_TYPE_ATTR, AUTH_TYPE_ANON);
    }

    private static String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
            .filter(c -> name.equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }

    /** Returns true if the string is a canonical UUID (e.g. 00000000-0000-0000-0000-000000000001). */
    private static boolean isCanonicalUuid(String s) {
        try {
            return UUID.fromString(s).toString().equals(s);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
