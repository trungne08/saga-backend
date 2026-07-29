package com.saga.be.integration.security;

import com.saga.be.config.IntegrationProperties;
import com.saga.be.exception.IntegrationException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class IntegrationSecretCipher {

    private static final String CURRENT_VERSION = "v2";
    private static final String LEGACY_VERSION = "v1";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final Pattern KEY_ID = Pattern.compile(
            "[A-Za-z0-9_-]{1,32}"
    );

    private final String activeKeyId;
    private final Map<String, String> configuredKeys;
    private final SecureRandom secureRandom = new SecureRandom();

    public IntegrationSecretCipher(IntegrationProperties properties) {
        this.activeKeyId = keyId(properties.tokenEncryptionKeyId());
        this.configuredKeys = configuredKeys(properties);
    }

    public String encrypt(String plaintext, String purpose) {
        if (plaintext == null) {
            return null;
        }
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key(activeKeyId), "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce)
            );
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8)
            );
            byte[] combined = ByteBuffer.allocate(nonce.length + ciphertext.length)
                    .put(nonce)
                    .put(ciphertext)
                    .array();
            return CURRENT_VERSION
                    + "."
                    + activeKeyId
                    + "."
                    + Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(combined);
        } catch (GeneralSecurityException exception) {
            throw encryptionFailure(exception);
        }
    }

    public String decrypt(String encoded, String purpose) {
        if (encoded == null) {
            return null;
        }
        if (encoded.startsWith(CURRENT_VERSION + ".")) {
            return decryptCurrent(encoded, purpose);
        }
        if (encoded.startsWith(LEGACY_VERSION + ".")) {
            return decryptLegacy(encoded, purpose);
        }
        throw encryptionFailure(null);
    }

    private String decryptCurrent(String encoded, String purpose) {
        String[] segments = encoded.split("\\.", 3);
        if (
            segments.length != 3
            || !KEY_ID.matcher(segments[1]).matches()
            || !configuredKeys.containsKey(segments[1])
        ) {
            throw encryptionFailure(null);
        }
        return decryptPayload(segments[2], purpose, key(segments[1]));
    }

    private String decryptLegacy(String encoded, String purpose) {
        String payload = encoded.substring(LEGACY_VERSION.length() + 1);
        IntegrationException lastFailure = null;
        for (String configuredKeyId : configuredKeys.keySet()) {
            try {
                return decryptPayload(
                        payload,
                        purpose,
                        key(configuredKeyId)
                );
            } catch (IntegrationException exception) {
                lastFailure = exception;
            }
        }
        throw encryptionFailure(lastFailure);
    }

    private String decryptPayload(
            String payload,
            String purpose,
            byte[] key
    ) {
        try {
            byte[] combined = Base64.getUrlDecoder().decode(payload);
            if (combined.length <= NONCE_BYTES) {
                throw new GeneralSecurityException("Invalid ciphertext");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            byte[] ciphertext = new byte[combined.length - NONCE_BYTES];
            System.arraycopy(combined, 0, nonce, 0, NONCE_BYTES);
            System.arraycopy(
                    combined,
                    NONCE_BYTES,
                    ciphertext,
                    0,
                    ciphertext.length
            );
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce)
            );
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw encryptionFailure(exception);
        }
    }

    private String keyId(String configuredKeyId) {
        String value = configuredKeyId == null || configuredKeyId.isBlank()
                ? "primary"
                : configuredKeyId.trim();
        if (!KEY_ID.matcher(value).matches()) {
            throw notConfigured();
        }
        return value;
    }

    private Map<String, String> configuredKeys(
            IntegrationProperties properties
    ) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(activeKeyId, properties.tokenEncryptionKey());
        String previous = properties.tokenEncryptionPreviousKeys();
        if (previous == null || previous.isBlank()) {
            return Map.copyOf(keys);
        }
        for (String entry : previous.split(",")) {
            String[] pair = entry.trim().split(":", 2);
            if (
                pair.length != 2
                || !KEY_ID.matcher(pair[0]).matches()
                || pair[1].isBlank()
                || keys.putIfAbsent(pair[0], pair[1]) != null
            ) {
                throw notConfigured();
            }
        }
        return Map.copyOf(keys);
    }

    private byte[] key(String keyId) {
        try {
            byte[] decoded = Base64.getDecoder().decode(
                    configuredKeys.get(keyId)
            );
            if (decoded.length != 32) {
                throw new IllegalArgumentException();
            }
            return decoded;
        } catch (RuntimeException exception) {
            throw notConfigured();
        }
    }

    private IntegrationException notConfigured() {
        return new IntegrationException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "INTEGRATION_ENCRYPTION_NOT_CONFIGURED",
                "Integration secret storage is not configured"
        );
    }

    private IntegrationException encryptionFailure(Throwable cause) {
        return new IntegrationException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "INTEGRATION_ENCRYPTION_FAILURE",
                "Integration secret storage is unavailable",
                cause
        );
    }
}
