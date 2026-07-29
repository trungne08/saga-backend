package com.saga.be.integration.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.saga.be.exception.IntegrationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class IntegrationAttemptLimiterTest {

    @Test
    void limitsEleventhAttemptWithinOneMinute() {
        IntegrationAttemptLimiter limiter = new IntegrationAttemptLimiter(
                Clock.fixed(
                        Instant.parse("2026-07-29T00:00:00Z"),
                        ZoneOffset.UTC
                )
        );
        for (int attempt = 0; attempt < 10; attempt++) {
            assertDoesNotThrow(() -> limiter.requireAllowed("student:github"));
        }

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> limiter.requireAllowed("student:github")
        );
        assertEquals("INTEGRATION_RATE_LIMITED", exception.getCode());
    }

    @Test
    void limitsAreIsolatedByAuthenticatedSubjectAndOperation() {
        IntegrationAttemptLimiter limiter = new IntegrationAttemptLimiter(
                Clock.systemUTC()
        );
        for (int attempt = 0; attempt < 10; attempt++) {
            limiter.requireAllowed("student-a:jira");
        }

        assertDoesNotThrow(() -> limiter.requireAllowed("student-b:jira"));
        assertDoesNotThrow(() -> limiter.requireAllowed("student-a:github"));
    }
}
