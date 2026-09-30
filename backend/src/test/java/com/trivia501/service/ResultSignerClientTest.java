package com.trivia501.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ResultSignerClient")
class ResultSignerClientTest {

    @Test
    @DisplayName("pseudonym is deterministic for the same player id")
    void pseudonymIsDeterministic() {
        UUID id = UUID.randomUUID();
        assertEquals(ResultSignerClient.playerPseudonym(id), ResultSignerClient.playerPseudonym(id));
    }

    @Test
    @DisplayName("pseudonym does not contain the raw id and is 64 hex chars")
    void pseudonymDoesNotContainRawId() {
        String raw = "00000000-0000-0000-0000-000000000001";
        String result = ResultSignerClient.playerPseudonym(UUID.fromString(raw));
        assertFalse(result.contains(raw));
        assertTrue(result.matches("^[0-9a-f]{64}$"));
    }

    @Test
    @DisplayName("different ids give different pseudonyms")
    void differentIdsGiveDifferentPseudonyms() {
        assertNotEquals(
                ResultSignerClient.playerPseudonym(UUID.randomUUID()),
                ResultSignerClient.playerPseudonym(UUID.randomUUID()));
    }
}
