package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class OidcAvatarUrlTest {

    @Test
    void acceptsBoundedHttpAndHttpsUrls() {
        assertEquals("https://cdn.example.test/a.png", OidcAvatarUrl.sanitize("https://cdn.example.test/a.png"));
        assertEquals("http://cdn.example.test/a.png", OidcAvatarUrl.sanitize("http://cdn.example.test/a.png"));
    }

    @Test
    void rejectsMissingUnsafeOrOversizedValues() {
        assertNull(OidcAvatarUrl.sanitize(null));
        assertNull(OidcAvatarUrl.sanitize(" "));
        assertNull(OidcAvatarUrl.sanitize("javascript:alert(1)"));
        assertNull(OidcAvatarUrl.sanitize("data:image/png;base64,abc"));
        assertNull(OidcAvatarUrl.sanitize("https://user:secret@cdn.example.test/a.png"));
        assertNull(OidcAvatarUrl.sanitize("not-a-url"));
        assertNull(OidcAvatarUrl.sanitize("https://cdn.example.test/" + "a".repeat(OidcAvatarUrl.MAX_LENGTH)));
    }
}
