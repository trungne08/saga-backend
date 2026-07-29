package com.saga.be.integration.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.saga.be.config.IntegrationProperties;
import com.saga.be.exception.IntegrationException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class IntegrationSecretCipherTest {

    @Test
    void encryptionUsesUniqueNonceAndPurposeAsAdditionalAuthenticatedData() {
        IntegrationSecretCipher cipher = cipher(key(), "active", null);

        String first = cipher.encrypt("secret", "jira:access");
        String second = cipher.encrypt("secret", "jira:access");

        assertTrue(first.startsWith("v2.active."));
        assertNotEquals(first, second);
        assertEquals("secret", cipher.decrypt(first, "jira:access"));
        assertThrows(
                IntegrationException.class,
                () -> cipher.decrypt(first, "jira:refresh")
        );
    }

    @Test
    void rotatedCipherDecryptsDataWrittenWithPreviousKey() {
        String oldKey = key();
        IntegrationSecretCipher oldCipher = cipher(oldKey, "old", null);
        String ciphertext = oldCipher.encrypt("refresh-token", "jira:refresh");

        IntegrationSecretCipher rotated = cipher(
                key(),
                "current",
                "old:" + oldKey
        );

        assertEquals(
                "refresh-token",
                rotated.decrypt(ciphertext, "jira:refresh")
        );
        assertTrue(
                rotated.encrypt("new-token", "jira:access")
                        .startsWith("v2.current.")
        );
    }

    private IntegrationSecretCipher cipher(
            String activeKey,
            String activeKeyId,
            String previousKeys
    ) {
        return new IntegrationSecretCipher(new IntegrationProperties(
                activeKey,
                activeKeyId,
                previousKeys,
                Duration.ofMinutes(10),
                Duration.ofSeconds(3),
                Duration.ofSeconds(10),
                true,
                Duration.ofMinutes(5)
        ));
    }

    private String key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
