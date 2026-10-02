package com.trivia501.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    private CorsConfiguration configFor(String origins) {
        CorsConfig cors = new CorsConfig();
        ReflectionTestUtils.setField(cors, "frontendOrigin", origins);
        return cors.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("POST", "/api/freeplay/start"));
    }

    @Test
    void allowsProdAndTeamPreviewsButNotOtherVercelApps() {
        CorsConfiguration config = configFor(
                "https://trivia-501.vercel.app, https://trivia-501-*-fanaticpurifiers-projects.vercel.app");

        assertThat(config.checkOrigin("https://trivia-501.vercel.app")).isNotNull();
        assertThat(config.checkOrigin(
                "https://trivia-501-git-refactor-game-s-d02eac-fanaticpurifiers-projects.vercel.app")).isNotNull();
        assertThat(config.checkOrigin("https://evil.vercel.app")).isNull();
        assertThat(config.checkOrigin("https://trivia-501-x-other-team.vercel.app")).isNull();
        assertThat(config.checkOrigin("http://localhost:3000")).isNotNull();
    }

    @Test
    void unsetSecretAllowsOnlyLocalhost() {
        CorsConfiguration config = configFor("");

        assertThat(config.checkOrigin("https://trivia-501.vercel.app")).isNull();
        assertThat(config.checkOrigin("http://localhost:3000")).isNotNull();
    }
}
