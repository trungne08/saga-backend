package com.saga.be.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class NotificationActionUrlValidatorTest {

    @Test
    void blankOrAbsentActionUrlIsNull() {
        assertThat(NotificationActionUrlValidator.normalizeOptionalHttps(null)).isNull();
        assertThat(NotificationActionUrlValidator.normalizeOptionalHttps("")).isNull();
        assertThat(NotificationActionUrlValidator.normalizeOptionalHttps("   ")).isNull();
    }

    @Test
    void httpsAbsoluteUrlIsTrimmedAndKept() {
        assertThat(NotificationActionUrlValidator.normalizeOptionalHttps("  https://example.com/resource  "))
                .isEqualTo("https://example.com/resource");
    }

    @Test
    void unsafeOrMalformedUrlsAreRejected() {
        for (String unsafe : new String[]{
                "http://example.com/resource",
                "javascript:alert(1)",
                "data:text/html,hi",
                "file:///tmp/x",
                "https://",
                "not-a-url"
        }) {
            try {
                String accepted = NotificationActionUrlValidator.normalizeOptionalHttps(unsafe);
                throw new AssertionError("accepted unsafe actionUrl=" + unsafe + " as " + accepted);
            } catch (ResponseStatusException expected) {
                assertThat(expected.getStatusCode().value()).isEqualTo(400);
            }
        }
    }

    @Test
    void controlCharactersAreRejected() {
        try {
            NotificationActionUrlValidator.normalizeOptionalHttps("https://example.com/path?x=" + (char) 7);
            throw new AssertionError("control character was accepted");
        } catch (ResponseStatusException expected) {
            assertThat(expected.getStatusCode().value()).isEqualTo(400);
        }
    }
}
