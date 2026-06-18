package com.trivia501.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OptionalJwtFilter")
class OptionalJwtFilterTest {

    private JwtDecoder jwtDecoder;
    private JwtAuthenticationConverter jwtConverter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);
        jwtConverter = mock(JwtAuthenticationConverter.class);
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt validJwt() {
        return Jwt.withTokenValue("header.body.sig")
            .header("alg", "HS256")
            .claim("sub", "user-uuid-123")
            .claim("role", "authenticated")
            .claim("email", "test@example.com")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    }

    private static Jwt adminJwt() {
        return Jwt.withTokenValue("header.body.sig")
            .header("alg", "HS256")
            .claim("sub", "admin-uuid-456")
            .claim("role", "authenticated")
            .claim("app_metadata", Map.of("role", "admin"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    }

    private static Jwt anonymousJwt() {
        return Jwt.withTokenValue("header.body.sig")
            .header("alg", "HS256")
            .claim("sub", "anon-uuid")
            .claim("role", "anonymous")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    }

    // ── JWT path — happy ─────────────────────────────────────────────────

    @Nested
    @DisplayName("JWT authentication — happy path")
    class JwtHappyPath {

        @Test
        @DisplayName("valid JWT sets JwtAuthenticationToken in context")
        void validJwtSetsAuthentication() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, true);
            var jwt = validJwt();
            var jwtAuth = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

            when(jwtDecoder.decode("test-token")).thenReturn(jwt);
            when(jwtConverter.convert(jwt)).thenReturn(jwtAuth);

            var request = requestWithBearer("Bearer test-token");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isInstanceOf(JwtAuthenticationToken.class);
            assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
            assertThat(request.getAttribute(OptionalJwtFilter.AUTH_TYPE_ATTR))
                .isEqualTo("jwt");
            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("principal.getName() returns JWT sub claim")
        void principalNameIsJwtSub() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, true);
            var jwt = validJwt(); // sub = "user-uuid-123"
            var jwtAuth = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

            when(jwtDecoder.decode("test-token")).thenReturn(jwt);
            when(jwtConverter.convert(jwt)).thenReturn(jwtAuth);

            var request = requestWithBearer("Bearer test-token");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getName()).isEqualTo("user-uuid-123");
        }

        @Test
        @DisplayName("JWT with admin app_metadata grants ROLE_ADMIN + ROLE_USER")
        void adminJwtGrantsBothRoles() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, true);
            var jwt = adminJwt();
            var jwtAuth = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN")));

            when(jwtDecoder.decode("admin-token")).thenReturn(jwt);
            when(jwtConverter.convert(jwt)).thenReturn(jwtAuth);

            var request = requestWithBearer("Bearer admin-token");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
            assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }

        @Test
        @DisplayName("JWT without admin claim grants ROLE_USER only")
        void regularJwtGrantsUserOnly() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, true);
            var jwt = validJwt();
            var jwtAuth = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

            when(jwtDecoder.decode("test-token")).thenReturn(jwt);
            when(jwtConverter.convert(jwt)).thenReturn(jwtAuth);

            var request = requestWithBearer("Bearer test-token");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getAuthorities())
                .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
        }
    }

    // ── JWT path — rejection ─────────────────────────────────────────────

    @Nested
    @DisplayName("JWT authentication — rejection")
    class JwtRejection {

        @Test
        @DisplayName("anonymous JWT falls back to cookie anon auth")
        void anonymousJwtFallsBackToCookie() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, true);
            var jwt = anonymousJwt();

            when(jwtDecoder.decode("anon-token")).thenReturn(jwt);

            var request = requestWithBearer("Bearer anon-token");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            // Should fall back to anonymous, not JWT
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isInstanceOf(UsernamePasswordAuthenticationToken.class);
            assertThat(request.getAttribute(OptionalJwtFilter.AUTH_TYPE_ATTR))
                .isEqualTo("anonymous");
            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("expired JWT returns 401 when secret is configured")
        void expiredJwtReturns401() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, true);

            when(jwtDecoder.decode("expired-token"))
                .thenThrow(new JwtException("Token expired"));

            var request = requestWithBearer("Bearer expired-token");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString())
                .contains("Invalid or expired token");
        }

        @Test
        @DisplayName("malformed Bearer token returns 401 when secret configured")
        void malformedTokenReturns401() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, true);

            when(jwtDecoder.decode("garbage"))
                .thenThrow(new JwtException("Malformed token"));

            var request = requestWithBearer("Bearer garbage");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("JwtException falls back to anonymous when secret NOT configured")
        void jwtExceptionFallsBackWhenNotConfigured() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, false);

            when(jwtDecoder.decode("bad-token"))
                .thenThrow(new JwtException("Bad token"));

            var request = requestWithBearer("Bearer bad-token");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            // Should fall back to anonymous
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isInstanceOf(UsernamePasswordAuthenticationToken.class);
            assertThat(request.getAttribute(OptionalJwtFilter.AUTH_TYPE_ATTR))
                .isEqualTo("anonymous");
        }

        @Test
        @DisplayName("AuthenticationException falls back to anonymous when not configured")
        void authExceptionFallsBackWhenNotConfigured() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, false);
            var jwt = validJwt();

            when(jwtDecoder.decode("test-token")).thenReturn(jwt);
            when(jwtConverter.convert(jwt))
                .thenThrow(new org.springframework.security.core.AuthenticationException("bad claims") {});

            var request = requestWithBearer("Bearer test-token");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        }

        @Test
        @DisplayName("AuthenticationException returns 401 when secret configured")
        void authExceptionReturns401WhenConfigured() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, true);
            var jwt = validJwt();

            when(jwtDecoder.decode("test-token")).thenReturn(jwt);
            when(jwtConverter.convert(jwt))
                .thenThrow(new org.springframework.security.core.AuthenticationException("bad claims") {});

            var request = requestWithBearer("Bearer test-token");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString())
                .contains("Invalid token claims");
        }
    }

    // ── Existing auth passthrough ────────────────────────────────────────

    @Nested
    @DisplayName("existing authentication passthrough")
    class ExistingAuthPassthrough {

        @Test
        @DisplayName("skips when real Authentication already in context")
        void skipsForExistingRealAuth() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, true);
            var existing = new JwtAuthenticationToken(validJwt(),
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(existing);

            var request = requestWithBearer("Bearer test-token");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            // The existing auth should be preserved
            assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isSameAs(existing);
        }

        @Test
        @DisplayName("replaces AnonymousAuthenticationToken")
        void replacesAnonymousToken() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, true);
            var anon = new AnonymousAuthenticationToken("anon-key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
            SecurityContextHolder.getContext().setAuthentication(anon);

            var request = requestWithBearer("Bearer test-token");
            var jwt = validJwt();
            var jwtAuth = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
            when(jwtDecoder.decode("test-token")).thenReturn(jwt);
            when(jwtConverter.convert(jwt)).thenReturn(jwtAuth);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotSameAs(anon);
            assertThat(auth).isInstanceOf(JwtAuthenticationToken.class);
        }
    }

    // ── Cookie path — creation ──────────────────────────────────────────

    @Nested
    @DisplayName("anonymous cookie — creation")
    class CookieCreation {

        @Test
        @DisplayName("creates new UUID cookie when none present")
        void createsNewCookie() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, false);
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isInstanceOf(UsernamePasswordAuthenticationToken.class);
            assertThat(auth.getName()).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            assertThat(request.getAttribute(OptionalJwtFilter.AUTH_TYPE_ATTR))
                .isEqualTo("anonymous");

            Cookie cookie = response.getCookie("X-Anonymous-Id");
            assertThat(cookie).isNotNull();
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getPath()).isEqualTo("/");
            assertThat(cookie.getMaxAge()).isEqualTo(86400);
            assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        }

        @Test
        @DisplayName("cookie is Secure on HTTPS requests")
        void cookieSecureOnHttps() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, false);
            var request = new MockHttpServletRequest();
            request.setSecure(true);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            Cookie cookie = response.getCookie("X-Anonymous-Id");
            assertThat(cookie).isNotNull();
            assertThat(cookie.getSecure()).isTrue();
        }

        @Test
        @DisplayName("cookie is NOT Secure on HTTP requests")
        void cookieNotSecureOnHttp() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, false);
            var request = new MockHttpServletRequest();
            request.setSecure(false);
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            Cookie cookie = response.getCookie("X-Anonymous-Id");
            assertThat(cookie).isNotNull();
            assertThat(cookie.getSecure()).isFalse();
        }
    }

    // ── Cookie path — reuse ─────────────────────────────────────────────

    @Nested
    @DisplayName("anonymous cookie — reuse")
    class CookieReuse {

        @Test
        @DisplayName("reuses existing cookie UUID and re-issues with sliding expiry")
        void reusesExistingCookie() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, false);
            var request = new MockHttpServletRequest();
            request.setCookies(new Cookie("X-Anonymous-Id", "existing-uuid-reuse"));
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getName()).isEqualTo("existing-uuid-reuse");

            Cookie cookie = response.getCookie("X-Anonymous-Id");
            assertThat(cookie).isNotNull();
            assertThat(cookie.getValue()).isEqualTo("existing-uuid-reuse");
            assertThat(cookie.getMaxAge()).isEqualTo(86400); // sliding window
        }
    }

    // ── Cookie rotation ─────────────────────────────────────────────────

    @Nested
    @DisplayName("anonymous cookie — rotation")
    class CookieRotation {

        @Test
        @DisplayName("rotates cookie when X-Rotate-Anonymous-Id is true")
        void rotatesCookieOnRequest() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, false);
            var request = new MockHttpServletRequest();
            request.setCookies(new Cookie("X-Anonymous-Id", "old-session-id"));
            // The filter sets AUTH_TYPE_ATTR during setAnonymousAuth;
            // controllers set ROTATE_ANON_ATTR before the chain runs.
            // In a real request, setAnonymousAuth runs first (sets ANON),
            // then the controller sets ROTATE_ANON_ATTR, then the post-chain
            // rotation check fires. Mock: we must set the attribute ourselves
            // to simulate a controller requesting rotation.
            request.setAttribute(OptionalJwtFilter.ROTATE_ANON_ATTR, "true");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            // Post-rotation: a new cookie should be issued.
            // Two cookies with the same name: the first from setAnonymousAuth ("old-session-id"),
            // the second from rotation (new UUID). getCookie() returns the first match,
            // so inspect the full list.
            Cookie[] cookies = response.getCookies();
            assertThat(cookies).hasSize(2);
            assertThat(cookies[0].getValue()).isEqualTo("old-session-id");
            assertThat(cookies[1].getValue()).isNotEqualTo("old-session-id");
            // The principal should be updated to the new UUID
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getName()).isEqualTo(cookies[1].getValue());
        }

        @Test
        @DisplayName("does NOT rotate when no rotation attribute")
        void noRotationWithoutAttribute() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, false);
            var request = new MockHttpServletRequest();
            request.setCookies(new Cookie("X-Anonymous-Id", "keep-me"));
            // No ROTATE_ANON_ATTR set
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            Cookie cookie = response.getCookie("X-Anonymous-Id");
            assertThat(cookie).isNotNull();
            assertThat(cookie.getValue()).isEqualTo("keep-me");
        }

        @Test
        @DisplayName("rotation is no-op on JWT-authenticated requests")
        void rotationNoOpForJwt() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, true);
            var jwt = validJwt();
            var jwtAuth = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
            when(jwtDecoder.decode("test-token")).thenReturn(jwt);
            when(jwtConverter.convert(jwt)).thenReturn(jwtAuth);

            var request = requestWithBearer("Bearer test-token");
            request.setAttribute(OptionalJwtFilter.ROTATE_ANON_ATTR, "true");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            // No cookie should be set (JWT path, not anon path)
            Cookie cookie = response.getCookie("X-Anonymous-Id");
            assertThat(cookie).isNull();
            // Auth should still be JWT, not replaced by anon
            assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOf(JwtAuthenticationToken.class);
        }
    }

    // ── Edge cases ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("no Authorization header → anonymous")
        void noAuthHeaderGoesAnonymous() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, true);
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOf(UsernamePasswordAuthenticationToken.class);
            assertThat(request.getAttribute(OptionalJwtFilter.AUTH_TYPE_ATTR))
                .isEqualTo("anonymous");
        }

        @Test
        @DisplayName("non-Bearer Authorization header → anonymous")
        void nonBearerHeaderGoesAnonymous() throws Exception {
            var filter = new OptionalJwtFilter(jwtDecoder, jwtConverter, true);
            var request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
            var response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOf(UsernamePasswordAuthenticationToken.class);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private static MockHttpServletRequest requestWithBearer(String header) {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", header);
        return request;
    }
}
