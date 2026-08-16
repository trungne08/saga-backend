package com.saga.be.service;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Validates optional Lecturer Course broadcast action URLs. Does not fetch or follow the URL.
 */
public final class NotificationActionUrlValidator {

    static final int MAX_ACTION_URL_LENGTH = 500;

    private NotificationActionUrlValidator() {
    }

    public static String normalizeOptionalHttps(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (containsControlCharacter(raw)) {
            throw invalidActionUrl();
        }
        String value = raw.trim();
        if (value.length() > MAX_ACTION_URL_LENGTH) {
            throw invalidActionUrl();
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw invalidActionUrl();
        }
        if (!uri.isAbsolute()
                || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getHost().isBlank()) {
            throw invalidActionUrl();
        }
        return value;
    }

    private static boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(codePoint ->
                Character.isISOControl(codePoint) || codePoint < 0x20 || codePoint == 0x7F);
    }

    private static ResponseStatusException invalidActionUrl() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "actionUrl is invalid");
    }
}
