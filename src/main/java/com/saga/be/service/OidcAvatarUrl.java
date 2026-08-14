package com.saga.be.service;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Validates optional OIDC {@code picture} values before they are persisted.
 * Invalid or absent values are ignored so login can continue.
 */
public final class OidcAvatarUrl {

    public static final int MAX_LENGTH = 2048;

    private OidcAvatarUrl() {
    }

    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > MAX_LENGTH) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) {
            return null;
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return null;
        }
        if (uri.getUserInfo() != null) {
            return null;
        }
        if (!uri.isAbsolute()) {
            return null;
        }
        return value;
    }
}
