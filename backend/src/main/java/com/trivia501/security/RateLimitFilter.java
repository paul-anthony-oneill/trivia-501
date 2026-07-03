package com.trivia501.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate limiter for MVP scale.
 * <p>
 * Limits anonymous players (cookie-based sessions) to 60 req/min and
 * authenticated players (JWT) to 100 req/min per client IP.
 * Excluded from the test profile to avoid exhausting the per-IP window
 * across test methods.
 * <p>
 * Client IP resolution: reads Fly's {@code Fly-Client-IP} header (set by
 * Fly.io's edge proxy and not spoofable by clients). Falls back to
 * {@code getRemoteAddr()} in local dev.
 */
@Component
@Profile("!test")
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** Fly.io edge proxy sets this header to the real client IP. */
    private static final String FLY_CLIENT_IP_HEADER = "Fly-Client-IP";

    private static final int ANONYMOUS_LIMIT = 60;
    private static final int AUTHENTICATED_LIMIT = 100;
    private static final long WINDOW_MS = 60_000;

    private record Window(AtomicInteger count, long resetAt) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Skip rate limiting for health checks
        if (request.getRequestURI().startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Resolve the real client IP: trust Fly-Client-IP in production,
        // fall back to getRemoteAddr() in local dev where the header isn't set.
        String flyIp = request.getHeader(FLY_CLIENT_IP_HEADER);
        String ip = flyIp != null && !flyIp.isBlank() ? flyIp : request.getRemoteAddr();

        boolean isJwt = "jwt".equals(request.getAttribute(OptionalJwtFilter.AUTH_TYPE_ATTR));
        String key = isJwt ? "auth:" + ip : "anon:" + ip;
        int limit = isJwt ? AUTHENTICATED_LIMIT : ANONYMOUS_LIMIT;

        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, w) -> {
            if (w == null || now > w.resetAt()) {
                return new Window(new AtomicInteger(1), now + WINDOW_MS);
            }
            w.count().incrementAndGet();
            return w;
        });
        // ponytail: window cleanup is handled by compute() above — it already
        // replaces expired windows. No separate sweep needed at this scale.

        if (window.count().get() > limit) {
            log.warn("Rate limit exceeded for {} (count={}, limit={})", key, window.count().get(), limit);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("{\"error\":\"Too many requests\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
