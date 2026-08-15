package com.saga.be.integration.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
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

    @Test
    void exactCommitDetailParsesChangedFilesWithoutExposingInstallationToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubProviderClientImpl client = client(pkcs8Pem(), builder);
        expectInstallationToken(server);
        server.expect(requestTo("https://api.github.test/repos/saga/backend/commits/"
                        + "0123456789abcdef0123456789abcdef01234567?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "sha":"0123456789abcdef0123456789abcdef01234567",
                          "commit":{"message":"Safe commit","committer":{"date":"2026-08-14T00:00:00Z"}},
                          "stats":{"additions":2,"deletions":1},
                          "files":[{"filename":"src/Main.java","status":"modified","additions":2,"deletions":1,"patch":"@@ safe patch"}]
                        }
                        """, MediaType.APPLICATION_JSON));

        GitHubCommitDetailSnapshot result = client.commitDetail(
                9001L,
                "saga",
                "backend",
                "0123456789abcdef0123456789abcdef01234567"
        );

        assertEquals("Safe commit", result.message());
        assertThat(result.changedFiles()).hasSize(1);
        assertEquals("src/Main.java", result.changedFiles().get(0).path());
        assertEquals("@@ safe patch", result.changedFiles().get(0).patch());
        assertThat(result.toString()).doesNotContain("synthetic-installation-token");
        server.verify();
    }

    @Test
    void commitDetailUsesExistingSafePermanentAndTemporaryProviderTaxonomy() {
        assertCommitFailure(HttpStatus.NOT_FOUND, 1, "GITHUB_RESOURCE_NOT_FOUND");
        assertCommitFailure(HttpStatus.TOO_MANY_REQUESTS, 3, "GITHUB_RATE_LIMITED");
        assertCommitFailure(HttpStatus.SERVICE_UNAVAILABLE, 3, "GITHUB_PROVIDER_UNAVAILABLE");
    }

    @Test
    void malformedCommitDetailIsRejectedWithSafeResponseCode() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubProviderClientImpl client = client(pkcs8Pem(), builder);
        expectInstallationToken(server);
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/commits/")))
                .andRespond(withSuccess("""
                        {"sha":"0123456789abcdef0123456789abcdef01234567",
                         "commit":{"message":"Safe commit"},"files":{"unexpected":true}}
                        """, MediaType.APPLICATION_JSON));

        IntegrationException failure = assertThrows(
                IntegrationException.class,
                () -> client.commitDetail(
                        9001L,
                        "saga",
                        "backend",
                        "0123456789abcdef0123456789abcdef01234567"
                )
        );

        assertEquals("GITHUB_RESPONSE_INVALID", failure.getCode());
        server.verify();
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
        return client(pem, RestClient.builder());
    }

    private GitHubProviderClientImpl client(String pem, RestClient.Builder builder) {
        return new GitHubProviderClientImpl(
                new GitHubIntegrationProperties(
                        true, "12345", "client", "secret", pem, "webhook", "saga",
                        "https://saga.test/setup", "https://api.github.test", "https://github.test",
                        "https://saga.test/personal", "https://saga.test/project", "https://saga.test/webhook"
                ),
                JsonMapper.builder().build(),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                builder.build()
        );
    }

    private void assertCommitFailure(
            HttpStatus status,
            int expectedAttempts,
            String expectedCode
    ) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubProviderClientImpl client = client(pkcs8Pem(), builder);
        expectInstallationToken(server);
        server.expect(
                        ExpectedCount.times(expectedAttempts),
                        requestTo(org.hamcrest.Matchers.containsString("/commits/"))
                )
                .andRespond(withStatus(status));

        IntegrationException failure = assertThrows(
                IntegrationException.class,
                () -> client.commitDetail(
                        9001L,
                        "saga",
                        "backend",
                        "0123456789abcdef0123456789abcdef01234567"
                )
        );

        assertEquals(expectedCode, failure.getCode());
        assertThat(failure.getMessage()).doesNotContain("synthetic-installation-token");
        server.verify();
    }

    private void expectInstallationToken(MockRestServiceServer server) {
        server.expect(requestTo(
                        "https://api.github.test/app/installations/9001/access_tokens"
                ))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"token":"synthetic-installation-token",
                         "expires_at":"2027-01-01T00:00:00Z"}
                        """, MediaType.APPLICATION_JSON));
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
