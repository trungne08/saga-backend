package com.saga.be.integration.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.saga.be.config.GitHubIntegrationProperties;
import com.saga.be.config.IntegrationProperties;
import com.saga.be.exception.IntegrationException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class GitHubProviderClientImplTest {

    private static KeyPair keyPair;

    @BeforeAll
    static void createKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @Test
    void signsJwtWithValidPkcs8Pem() throws Exception {
        assertValidJwt(pkcs8Pem());
    }

    @Test
    void signsJwtWithValidPkcs1RsaPem() throws Exception {
        assertValidJwt(pkcs1Pem());
    }

    @Test
    void acceptsLiteralNewlinesAndCrLfPem() throws Exception {
        assertValidJwt(pkcs1Pem().replace("\n", "\\n"));
        assertValidJwt(pkcs8Pem().replace("\n", "\r\n"));
    }

    @Test
    void malformedPemReturnsOnlyTheSafeKeyError() {
        String secretMarker = "PRIVATE_KEY_MUST_NOT_APPEAR";
        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> client("-----BEGIN RSA PRIVATE KEY-----\n"
                        + secretMarker
                        + "\n-----END RSA PRIVATE KEY-----").appJwt()
        );

        assertEquals("GITHUB_APP_KEY_INVALID", exception.getCode());
        assertThat(exception.getMessage()).doesNotContain(secretMarker);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void unsupportedKeyTypeIsRejectedSafely() {
        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> client("-----BEGIN EC PRIVATE KEY-----\nAA==\n-----END EC PRIVATE KEY-----").appJwt()
        );

        assertEquals("GITHUB_APP_KEY_INVALID", exception.getCode());
        assertThat(exception.getMessage()).doesNotContain("EC PRIVATE KEY");
    }

    private void assertValidJwt(String pem) throws Exception {
        String jwt = client(pem).appJwt();
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length);
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    private GitHubProviderClientImpl client(String pem) {
        return new GitHubProviderClientImpl(
                new GitHubIntegrationProperties(
                        true, "12345", "client", "secret", pem, "webhook", "saga",
                        "https://saga.test/setup", "https://api.github.test", "https://github.test",
                        "https://saga.test/personal", "https://saga.test/project", "https://saga.test/webhook"
                ),
                JsonMapper.builder().build(),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                RestClient.builder().build()
        );
    }

    private String pkcs8Pem() {
        return pem("PRIVATE KEY", keyPair.getPrivate().getEncoded(), "\n");
    }

    private String pkcs1Pem() {
        RSAPrivateCrtKey key = (RSAPrivateCrtKey) keyPair.getPrivate();
        byte[] der = sequence(
                integer(BigInteger.ZERO),
                integer(key.getModulus()),
                integer(key.getPublicExponent()),
                integer(key.getPrivateExponent()),
                integer(key.getPrimeP()),
                integer(key.getPrimeQ()),
                integer(key.getPrimeExponentP()),
                integer(key.getPrimeExponentQ()),
                integer(key.getCrtCoefficient())
        );
        return pem("RSA PRIVATE KEY", der, "\n");
    }

    private String pem(String type, byte[] der, String newline) {
        String base64 = Base64.getMimeEncoder(64, newline.getBytes(StandardCharsets.US_ASCII))
                .encodeToString(der);
        return "-----BEGIN " + type + "-----" + newline
                + base64 + newline
                + "-----END " + type + "-----";
    }

    private byte[] sequence(byte[]... elements) {
        return der(0x30, concat(elements));
    }

    private byte[] integer(BigInteger value) {
        return der(0x02, value.toByteArray());
    }

    private byte[] der(int tag, byte[] value) {
        return concat(new byte[]{(byte) tag}, length(value.length), value);
    }

    private byte[] length(int length) {
        if (length < 128) {
            return new byte[]{(byte) length};
        }
        int bytes = 0;
        for (int value = length; value > 0; value >>>= 8) {
            bytes++;
        }
        byte[] encoded = new byte[bytes + 1];
        encoded[0] = (byte) (0x80 | bytes);
        for (int index = bytes; index > 0; index--) {
            encoded[index] = (byte) (length & 0xff);
            length >>>= 8;
        }
        return encoded;
    }

    private byte[] concat(byte[]... values) {
        int length = java.util.Arrays.stream(values).mapToInt(value -> value.length).sum();
        byte[] result = new byte[length];
        int position = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, result, position, value.length);
            position += value.length;
        }
        return result;
    }
}
