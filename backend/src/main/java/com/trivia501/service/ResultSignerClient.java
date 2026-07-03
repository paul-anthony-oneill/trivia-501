package com.trivia501.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * HTTP client for the go-signer microservice.
 *
 * <p>Configured via {@code result-signer.url} in application.yml (set as
 * {@code RESULT_SIGNER_URL} in production). When the URL is blank the client
 * is effectively disabled and always returns empty — safe for local dev without
 * the Go service running.
 *
 * <p>Uses Spring's {@link RestClient} (Spring 6.1+) with a short timeout.
 * Signing failure is always non-fatal: callers receive {@link Optional#empty()}
 * and the game completes normally.
 */
@Service
@Slf4j
public class ResultSignerClient {

    private static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT;

    private final RestClient restClient;
    private final String signerUrl;
    private final String internalSecret;

    public ResultSignerClient(
            @Value("${result-signer.url:}") String signerUrl,
            @Value("${result-signer.secret:}") String internalSecret
    ) {
        this.signerUrl = signerUrl;
        this.internalSecret = internalSecret;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl(signerUrl.isBlank() ? "http://localhost:8090" : signerUrl)
                .requestFactory(factory)
                .build();
    }

    public Optional<String> sign(UUID gameId, UUID playerId, int finalScore, LocalDateTime completedAt) {
        if (signerUrl == null || signerUrl.isBlank()) {
            log.debug("result-signer.url not configured — skipping signing for game {}", gameId);
            return Optional.empty();
        }

        try {
            String completedAtIso = completedAt.atOffset(ZoneOffset.UTC).format(ISO_INSTANT);
            SignRequest request = new SignRequest(
                    gameId.toString(),
                    playerId.toString(),
                    finalScore,
                    completedAtIso
            );

            var postSpec = restClient.post()
                    .uri("/sign")
                    .body(request);
            if (internalSecret != null && !internalSecret.isBlank()) {
                postSpec = postSpec.header("Authorization", "Bearer " + internalSecret);
            }
            SignResponse response = postSpec.retrieve().body(SignResponse.class);

            if (response == null || response.token() == null) {
                log.warn("go-signer returned empty response for game {}", gameId);
                return Optional.empty();
            }

            // Serialise the token object to a compact JSON string for storage.
            // Use Jackson to avoid corrupt JSON from quote/backslash in payload or sig.
            String tokenJson = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(new SignedToken(
                            response.token().payload(), response.token().sig()));
            return Optional.of(tokenJson);

        } catch (Exception e) {
            // Signing failure must never break a checkout — log and continue.
            log.error("go-signer unavailable for game {}: {}", gameId, e.getMessage());
            return Optional.empty();
        }
    }

    // ── Wire-format records ───────────────────────────────────────────────────

    private record SignRequest(
            @JsonProperty("gameId") String gameId,
            @JsonProperty("playerId") String playerId,
            @JsonProperty("finalScore") int finalScore,
            @JsonProperty("completedAt") String completedAt
    ) {}

    private record SignResponse(
            @JsonProperty("token") SignerToken token
    ) {}

    private record SignerToken(
            @JsonProperty("payload") String payload,
            @JsonProperty("sig") String sig
    ) {}

    /** Wire format for the stored token JSON. */
    private record SignedToken(String payload, String sig) {}
}
