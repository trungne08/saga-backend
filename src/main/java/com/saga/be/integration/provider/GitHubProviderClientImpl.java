package com.saga.be.integration.provider;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.saga.be.config.GitHubIntegrationProperties;
import com.saga.be.config.IntegrationProperties;
import com.saga.be.exception.IntegrationException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@ConditionalOnProperty(
        prefix = "app.integrations.github",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class GitHubProviderClientImpl implements GitHubProviderClient {

    private static final int MAX_GET_ATTEMPTS = 3;
    private static final Duration TOKEN_EXPIRY_SKEW = Duration.ofMinutes(1);

    private final GitHubIntegrationProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Clock clock;
    private final ConcurrentHashMap<Long, CachedInstallationToken> tokenCache =
            new ConcurrentHashMap<>();

    @Autowired
    public GitHubProviderClientImpl(
            GitHubIntegrationProperties properties,
            IntegrationProperties integrationProperties,
            ObjectMapper objectMapper
    ) {
        this(
                properties,
                objectMapper,
                Clock.systemUTC(),
                RestClient.builder()
                        .requestFactory(requestFactory(integrationProperties))
                        .defaultHeader(
                                HttpHeaders.ACCEPT,
                                "application/vnd.github+json"
                        )
                        .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                        .build()
        );
    }

    GitHubProviderClientImpl(
            GitHubIntegrationProperties properties,
            ObjectMapper objectMapper,
            Clock clock,
            RestClient restClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.restClient = restClient;
    }

    @Override
    public URI userAuthorizationUri(String state, String callbackUrl) {
        return UriComponentsBuilder.fromUriString(properties.webBaseUrl())
                .path("/login/oauth/authorize")
                .queryParam(
                        "client_id",
                        requireConfigured(properties.clientId(), "GITHUB_CLIENT_ID")
                )
                .queryParam("state", state)
                .queryParam(
                        "redirect_uri",
                        requireConfigured(callbackUrl, "GITHUB_CALLBACK_URL")
                )
                .build()
                .encode()
                .toUri();
    }

    @Override
    public String exchangeUserCode(String code, String callbackUrl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add(
                "client_id",
                requireConfigured(properties.clientId(), "GITHUB_CLIENT_ID")
        );
        form.add(
                "client_secret",
                requireConfigured(
                        properties.clientSecret(),
                        "GITHUB_CLIENT_SECRET"
                )
        );
        form.add("code", code);
        form.add(
                "redirect_uri",
                requireConfigured(callbackUrl, "GITHUB_CALLBACK_URL")
        );
        try {
            JsonNode response = restClient.post()
                    .uri(properties.webBaseUrl() + "/login/oauth/access_token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || response.hasNonNull("error")) {
                throw IntegrationException.invalid(
                        "GITHUB_OAUTH_REJECTED",
                        "GitHub authorization could not be completed"
                );
            }
            return requiredText(response, "access_token");
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        }
    }

    @Override
    public GitHubUserIdentity currentUser(String userAccessToken) {
        JsonNode user = get(apiUri("/user"), userAccessToken);
        return new GitHubUserIdentity(
                requiredLong(user, "id"),
                requiredText(user, "login"),
                text(user, "name"),
                text(user, "email")
        );
    }

    @Override
    public URI installationUri(String state) {
        return UriComponentsBuilder.fromUriString(properties.webBaseUrl())
                .pathSegment(
                        "apps",
                        requireConfigured(properties.appSlug(), "GITHUB_APP_SLUG"),
                        "installations",
                        "new"
                )
                .queryParam("state", state)
                .build()
                .encode()
                .toUri();
    }

    @Override
    public GitHubInstallationInfo installation(long installationId) {
        JsonNode response = get(
                apiUri("/app/installations/" + installationId),
                appJwt()
        );
        JsonNode account = response.path("account");
        return new GitHubInstallationInfo(
                requiredLong(response, "id"),
                requiredText(account, "login"),
                requiredText(account, "type"),
                response.hasNonNull("suspended_at")
        );
    }

    @Override
    public boolean userCanAccessInstallation(
            String userAccessToken,
            long installationId
    ) {
        int page = 1;
        while (page <= 100) {
            JsonNode response = get(
                    UriComponentsBuilder.fromUri(apiUri("/user/installations"))
                            .queryParam("per_page", 100)
                            .queryParam("page", page)
                            .build()
                            .encode()
                            .toUri(),
                    userAccessToken
            );
            JsonNode installations = response.path("installations");
            if (!installations.isArray()) {
                throw invalidResponse();
            }
            for (JsonNode installation : installations) {
                if (
                    installation.path("id").canConvertToLong()
                    && installation.path("id").asLong() == installationId
                ) {
                    return true;
                }
            }
            if (installations.size() < 100) {
                return false;
            }
            page++;
        }
        throw IntegrationException.unavailable(
                "GITHUB_INSTALLATION_PAGINATION_LIMIT"
        );
    }

    @Override
    public List<GitHubRepositoryInfo> installationRepositories(
            long installationId
    ) {
        String token = installationAccessToken(installationId);
        List<GitHubRepositoryInfo> repositories = new ArrayList<>();
        int page = 1;
        while (true) {
            URI uri = UriComponentsBuilder.fromUri(
                            apiUri("/installation/repositories")
                    )
                    .queryParam("per_page", 100)
                    .queryParam("page", page)
                    .build()
                    .toUri();
            JsonNode response = get(uri, token);
            JsonNode values = response.path("repositories");
            if (!values.isArray()) {
                throw invalidResponse();
            }
            values.forEach(repository -> repositories.add(toRepository(repository)));
            if (values.size() < 100) {
                return repositories;
            }
            page++;
        }
    }

    @Override
    public List<GitHubIssueSnapshot> issues(
            long installationId,
            String owner,
            String repository,
            LocalDateTime since
    ) {
        String token = installationAccessToken(installationId);
        List<GitHubIssueSnapshot> issues = new ArrayList<>();
        int page = 1;
        while (true) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUri(
                            apiUri("/repos/" + safePath(owner)
                                    + "/" + safePath(repository) + "/issues")
                    )
                    .queryParam("state", "all")
                    .queryParam("sort", "updated")
                    .queryParam("direction", "asc")
                    .queryParam("per_page", 100)
                    .queryParam("page", page);
            if (since != null) {
                builder.queryParam(
                        "since",
                        since.atOffset(ZoneOffset.UTC).toString()
                );
            }
            JsonNode response = get(builder.build().encode().toUri(), token);
            if (!response.isArray()) {
                throw invalidResponse();
            }
            response.forEach(issue -> issues.add(toIssue(issue)));
            if (response.size() < 100) {
                return issues;
            }
            page++;
        }
    }

    @Override
    public List<GitHubCommitSnapshot> commits(
            long installationId,
            String owner,
            String repository,
            LocalDateTime since
    ) {
        String token = installationAccessToken(installationId);
        List<GitHubCommitSnapshot> commits = new ArrayList<>();
        int page = 1;
        while (true) {
            UriComponentsBuilder builder = repositoryUriBuilder(
                            owner,
                            repository,
                            "/commits"
                    )
                    .queryParam("per_page", 100)
                    .queryParam("page", page);
            if (since != null) {
                builder.queryParam(
                        "since",
                        since.atOffset(ZoneOffset.UTC).toString()
                );
            }
            JsonNode response = get(builder.build().encode().toUri(), token);
            if (!response.isArray()) {
                throw invalidResponse();
            }
            for (JsonNode summary : response) {
                String sha = requiredText(summary, "sha");
                JsonNode detail = get(repositoryUriBuilder(
                                owner,
                                repository,
                                "/commits/" + safePath(sha)
                        )
                        .build()
                        .encode()
                        .toUri(), token);
                commits.add(toCommit(detail));
            }
            if (response.size() < 100) {
                return commits;
            }
            page++;
        }
    }

    @Override
    public List<GitHubPullRequestSnapshot> pullRequests(
            long installationId,
            String owner,
            String repository,
            LocalDateTime since
    ) {
        String token = installationAccessToken(installationId);
        List<GitHubPullRequestSnapshot> pulls = new ArrayList<>();
        int page = 1;
        while (true) {
            URI uri = repositoryUriBuilder(owner, repository, "/pulls")
                    .queryParam("state", "all")
                    .queryParam("sort", "updated")
                    .queryParam("direction", "asc")
                    .queryParam("per_page", 100)
                    .queryParam("page", page)
                    .build()
                    .encode()
                    .toUri();
            JsonNode response = get(uri, token);
            if (!response.isArray()) {
                throw invalidResponse();
            }
            response.forEach(node -> {
                GitHubPullRequestSnapshot pull = toPullRequest(node);
                if (since == null || pull.updatedAt() == null
                        || !pull.updatedAt().isBefore(since)) {
                    pulls.add(pull);
                }
            });
            if (response.size() < 100) {
                return pulls;
            }
            page++;
        }
    }

    @Override
    public List<GitHubReviewSnapshot> reviews(
            long installationId,
            String owner,
            String repository,
            int pullNumber
    ) {
        String token = installationAccessToken(installationId);
        JsonNode response = get(repositoryUriBuilder(
                        owner,
                        repository,
                        "/pulls/" + pullNumber + "/reviews"
                )
                .queryParam("per_page", 100)
                .build()
                .encode()
                .toUri(), token);
        if (!response.isArray()) {
            throw invalidResponse();
        }
        List<GitHubReviewSnapshot> reviews = new ArrayList<>();
        response.forEach(review -> reviews.add(new GitHubReviewSnapshot(
                requiredLong(review, "id"),
                pullNumber,
                nullableLong(review.path("user"), "id"),
                requiredText(review, "state"),
                parseDateTime(text(review, "submitted_at")),
                parseDateTime(text(review, "submitted_at"))
        )));
        return reviews;
    }

    @Override
    public List<GitHubCommentSnapshot> issueComments(
            long installationId,
            String owner,
            String repository,
            LocalDateTime since
    ) {
        return comments(
                installationId,
                owner,
                repository,
                "/issues/comments",
                "issue_url",
                false,
                since
        );
    }

    @Override
    public List<GitHubCommentSnapshot> reviewComments(
            long installationId,
            String owner,
            String repository,
            LocalDateTime since
    ) {
        return comments(
                installationId,
                owner,
                repository,
                "/pulls/comments",
                "pull_request_url",
                true,
                since
        );
    }

    @Override
    public String installationAccessToken(long installationId) {
        CachedInstallationToken cached = tokenCache.get(installationId);
        if (
            cached != null
            && cached.expiresAt().minus(TOKEN_EXPIRY_SKEW).isAfter(clock.instant())
        ) {
            return cached.token();
        }
        synchronized (tokenCache) {
            cached = tokenCache.get(installationId);
            if (
                cached != null
                && cached.expiresAt()
                        .minus(TOKEN_EXPIRY_SKEW)
                        .isAfter(clock.instant())
            ) {
                return cached.token();
            }
            CachedInstallationToken fresh = createInstallationToken(
                    installationId
            );
            tokenCache.put(installationId, fresh);
            return fresh.token();
        }
    }

    @Override
    public void invalidateInstallationToken(long installationId) {
        tokenCache.remove(installationId);
    }

    String appJwt() {
        Instant now = clock.instant();
        Map<String, Object> header = Map.of("alg", "RS256", "typ", "JWT");
        Map<String, Object> claims = Map.of(
                "iat", now.minusSeconds(60).getEpochSecond(),
                "exp", now.plus(Duration.ofMinutes(9)).getEpochSecond(),
                "iss", requireConfigured(properties.appId(), "GITHUB_APP_ID")
        );
        try {
            String signingInput = base64Url(objectMapper.writeValueAsBytes(header))
                    + "."
                    + base64Url(objectMapper.writeValueAsBytes(claims));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey());
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + base64Url(signature.sign());
        } catch (GeneralSecurityException | JacksonException exception) {
            throw new IntegrationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "GITHUB_APP_KEY_INVALID",
                    "GitHub App authentication is not configured correctly"
            );
        }
    }

    private CachedInstallationToken createInstallationToken(long installationId) {
        try {
            JsonNode response = restClient.post()
                    .uri(apiUri(
                            "/app/installations/"
                                    + installationId
                                    + "/access_tokens"
                    ))
                    .headers(headers -> bearer(headers, appJwt()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw invalidResponse();
            }
            return new CachedInstallationToken(
                    requiredText(response, "token"),
                    OffsetDateTime.parse(requiredText(response, "expires_at"))
                            .toInstant()
            );
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        }
    }

    private JsonNode get(URI uri, String token) {
        for (int attempt = 1; attempt <= MAX_GET_ATTEMPTS; attempt++) {
            try {
                JsonNode response = restClient.get()
                        .uri(uri)
                        .headers(headers -> bearer(headers, token))
                        .retrieve()
                        .body(JsonNode.class);
                if (response == null) {
                    throw invalidResponse();
                }
                return response;
            } catch (RestClientResponseException exception) {
                if (attempt == MAX_GET_ATTEMPTS || !retryable(exception)) {
                    throw translate(exception);
                }
            }
        }
        throw IntegrationException.unavailable("GITHUB_PROVIDER_UNAVAILABLE");
    }

    private GitHubRepositoryInfo toRepository(JsonNode repository) {
        JsonNode owner = repository.path("owner");
        return new GitHubRepositoryInfo(
                requiredLong(repository, "id"),
                requiredText(owner, "login"),
                requiredText(repository, "name"),
                requiredText(repository, "full_name"),
                text(repository, "html_url"),
                text(repository, "default_branch")
        );
    }

    private GitHubIssueSnapshot toIssue(JsonNode issue) {
        JsonNode user = issue.path("user");
        JsonNode assignee = issue.path("assignee");
        return new GitHubIssueSnapshot(
                requiredLong(issue, "id"),
                text(issue, "node_id"),
                requiredInt(issue, "number"),
                text(issue, "title"),
                requiredText(issue, "state"),
                nullableLong(user, "id"),
                nullableLong(assignee, "id"),
                issue.hasNonNull("pull_request"),
                parseDateTime(text(issue, "updated_at")),
                parseDateTime(text(issue, "closed_at"))
        );
    }

    private GitHubCommitSnapshot toCommit(JsonNode commit) {
        JsonNode gitCommit = commit.path("commit");
        JsonNode author = commit.path("author");
        JsonNode stats = commit.path("stats");
        JsonNode files = commit.path("files");
        LocalDateTime timestamp = parseDateTime(
                nestedText(gitCommit, "author", "date")
        );
        return new GitHubCommitSnapshot(
                requiredText(commit, "sha"),
                nullableLong(author, "id"),
                text(gitCommit, "message"),
                timestamp,
                nullableInt(stats, "additions"),
                nullableInt(stats, "deletions"),
                files.isArray() ? files.size() : null,
                timestamp
        );
    }

    private GitHubPullRequestSnapshot toPullRequest(JsonNode pull) {
        return new GitHubPullRequestSnapshot(
                requiredLong(pull, "id"),
                text(pull, "node_id"),
                requiredInt(pull, "number"),
                text(pull, "title"),
                requiredText(pull, "state"),
                pull.path("draft").asBoolean(false),
                nullableLong(pull.path("user"), "id"),
                parseDateTime(text(pull, "merged_at")),
                parseDateTime(text(pull, "updated_at")),
                pull.path("review_comments").asInt(0),
                pull.path("comments").asInt(0)
        );
    }

    private List<GitHubCommentSnapshot> comments(
            long installationId,
            String owner,
            String repository,
            String path,
            String targetUrlField,
            boolean reviewComment,
            LocalDateTime since
    ) {
        String token = installationAccessToken(installationId);
        List<GitHubCommentSnapshot> comments = new ArrayList<>();
        int page = 1;
        while (true) {
            UriComponentsBuilder builder = repositoryUriBuilder(
                            owner,
                            repository,
                            path
                    )
                    .queryParam("sort", "updated")
                    .queryParam("direction", "asc")
                    .queryParam("per_page", 100)
                    .queryParam("page", page);
            if (since != null) {
                builder.queryParam(
                        "since",
                        since.atOffset(ZoneOffset.UTC).toString()
                );
            }
            JsonNode response = get(builder.build().encode().toUri(), token);
            if (!response.isArray()) {
                throw invalidResponse();
            }
            response.forEach(comment -> comments.add(new GitHubCommentSnapshot(
                    requiredLong(comment, "id"),
                    numberFromUrl(requiredText(comment, targetUrlField)),
                    nullableLong(comment.path("user"), "id"),
                    text(comment, "body"),
                    reviewComment || comment.hasNonNull("pull_request_review_id"),
                    parseDateTime(text(comment, "updated_at"))
            )));
            if (response.size() < 100) {
                return comments;
            }
            page++;
        }
    }

    private UriComponentsBuilder repositoryUriBuilder(
            String owner,
            String repository,
            String suffix
    ) {
        return UriComponentsBuilder.fromUri(
                apiUri(
                        "/repos/"
                                + safePath(owner)
                                + "/"
                                + safePath(repository)
                                + suffix
                )
        );
    }

    private int numberFromUrl(String url) {
        int separator = url.lastIndexOf('/');
        if (separator < 0 || separator == url.length() - 1) {
            throw invalidResponse();
        }
        try {
            return Integer.parseInt(url.substring(separator + 1));
        } catch (NumberFormatException exception) {
            throw invalidResponse();
        }
    }

    private PrivateKey privateKey() throws GeneralSecurityException {
        String pem = requireConfigured(
                properties.privateKey(),
                "GITHUB_PRIVATE_KEY"
        ).replace("\\n", "\n").trim();
        boolean pkcs8 = pem.startsWith("-----BEGIN PRIVATE KEY-----")
                && pem.endsWith("-----END PRIVATE KEY-----");
        boolean pkcs1 = pem.startsWith("-----BEGIN RSA PRIVATE KEY-----")
                && pem.endsWith("-----END RSA PRIVATE KEY-----");
        if (!pkcs8 && !pkcs1) {
            throw new InvalidKeySpecException("Unsupported private key type");
        }
        String encoded = pem
                .replace(pkcs8 ? "-----BEGIN PRIVATE KEY-----" : "-----BEGIN RSA PRIVATE KEY-----", "")
                .replace(pkcs8 ? "-----END PRIVATE KEY-----" : "-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new GeneralSecurityException("Invalid private key", exception);
        }
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return pkcs8
                ? keyFactory.generatePrivate(new PKCS8EncodedKeySpec(bytes))
                : keyFactory.generatePrivate(pkcs1Spec(bytes));
    }

    private RSAPrivateCrtKeySpec pkcs1Spec(byte[] der) throws GeneralSecurityException {
        try {
            DerReader sequence = new DerReader(der).readSequence();
            if (!BigInteger.ZERO.equals(sequence.readInteger())) {
                throw new InvalidKeySpecException("Unsupported RSA private key version");
            }
            RSAPrivateCrtKeySpec spec = new RSAPrivateCrtKeySpec(
                    sequence.readInteger(),
                    sequence.readInteger(),
                    sequence.readInteger(),
                    sequence.readInteger(),
                    sequence.readInteger(),
                    sequence.readInteger(),
                    sequence.readInteger(),
                    sequence.readInteger()
            );
            if (sequence.hasRemaining()) {
                throw new InvalidKeySpecException("Invalid RSA private key");
            }
            return spec;
        } catch (IllegalArgumentException exception) {
            throw new InvalidKeySpecException("Invalid RSA private key", exception);
        }
    }

    private void bearer(HttpHeaders headers, String token) {
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.valueOf("application/vnd.github+json")));
        headers.set("X-GitHub-Api-Version", "2022-11-28");
    }

    private URI apiUri(String path) {
        return UriComponentsBuilder.fromUriString(properties.apiBaseUrl())
                .path(path)
                .build()
                .encode()
                .toUri();
    }

    private boolean retryable(RestClientResponseException exception) {
        return exception.getStatusCode().is5xxServerError()
                || exception.getStatusCode().value() == 429;
    }

    private IntegrationException translate(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 401 || status == 403) {
            return IntegrationException.conflict(
                    "GITHUB_ACCESS_REVOKED",
                    "GitHub access is invalid, suspended, or has been revoked"
            );
        }
        if (status == 404) {
            return IntegrationException.conflict(
                    "GITHUB_RESOURCE_NOT_FOUND",
                    "The GitHub installation or repository is no longer accessible"
            );
        }
        if (status == 429) {
            return new IntegrationException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "GITHUB_RATE_LIMITED",
                    "GitHub rate limit was reached"
            );
        }
        return IntegrationException.unavailable("GITHUB_PROVIDER_UNAVAILABLE");
    }

    private String requireConfigured(String value, String variable) {
        if (value == null || value.isBlank()) {
            throw new IntegrationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "GITHUB_NOT_CONFIGURED",
                    variable + " is not configured"
            );
        }
        return value;
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw invalidResponse();
        }
        return value;
    }

    private long requiredLong(JsonNode node, String field) {
        Long value = nullableLong(node, field);
        if (value == null || value <= 0) {
            throw invalidResponse();
        }
        return value;
    }

    private int requiredInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw invalidResponse();
        }
        return value.asInt();
    }

    private Long nullableLong(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || !value.canConvertToLong()
                ? null
                : value.asLong();
    }

    private Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || !value.canConvertToInt()
                ? null
                : value.asInt();
    }

    private String nestedText(JsonNode node, String parent, String field) {
        JsonNode value = node == null ? null : node.path(parent);
        return value == null || value.isMissingNode() || value.isNull()
                ? null
                : text(value, field);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private LocalDateTime parseDateTime(String value) {
        return value == null ? null : OffsetDateTime.parse(value).toLocalDateTime();
    }

    private String safePath(String value) {
        if (
            value == null
            || !value.matches("[A-Za-z0-9_.-]{1,100}")
        ) {
            throw IntegrationException.invalid(
                    "GITHUB_REPOSITORY_INVALID",
                    "The GitHub repository identifier is invalid"
            );
        }
        return value;
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private IntegrationException invalidResponse() {
        return IntegrationException.unavailable("GITHUB_RESPONSE_INVALID");
    }

    private record CachedInstallationToken(String token, Instant expiresAt) {
    }

    /** Minimal DER reader for the PKCS#1 RSAPrivateKey sequence. */
    private static final class DerReader {
        private final byte[] value;
        private int position;
        private final int limit;

        private DerReader(byte[] value) {
            this(value, 0, value.length);
        }

        private DerReader(byte[] value, int position, int limit) {
            this.value = value;
            this.position = position;
            this.limit = limit;
        }

        private DerReader readSequence() {
            requireTag(0x30);
            int length = readLength();
            int end = checkedEnd(length);
            DerReader result = new DerReader(value, position, end);
            position = end;
            if (hasRemaining()) {
                throw new IllegalArgumentException("Trailing DER data");
            }
            return result;
        }

        private BigInteger readInteger() {
            requireTag(0x02);
            int length = readLength();
            if (length == 0) {
                throw new IllegalArgumentException("Empty DER integer");
            }
            int end = checkedEnd(length);
            byte[] integer = java.util.Arrays.copyOfRange(value, position, end);
            position = end;
            BigInteger result = new BigInteger(integer);
            if (result.signum() < 0) {
                throw new IllegalArgumentException("Negative DER integer");
            }
            return result;
        }

        private void requireTag(int expected) {
            if (!hasRemaining() || (value[position++] & 0xff) != expected) {
                throw new IllegalArgumentException("Unexpected DER tag");
            }
        }

        private int readLength() {
            if (!hasRemaining()) {
                throw new IllegalArgumentException("Missing DER length");
            }
            int first = value[position++] & 0xff;
            if ((first & 0x80) == 0) {
                return first;
            }
            int bytes = first & 0x7f;
            if (bytes == 0 || bytes > 4 || bytes > limit - position) {
                throw new IllegalArgumentException("Invalid DER length");
            }
            int length = 0;
            for (int index = 0; index < bytes; index++) {
                length = (length << 8) | (value[position++] & 0xff);
            }
            return length;
        }

        private int checkedEnd(int length) {
            if (length < 0 || length > limit - position) {
                throw new IllegalArgumentException("Invalid DER length");
            }
            return position + length;
        }

        private boolean hasRemaining() {
            return position < limit;
        }
    }

    private static JdkClientHttpRequestFactory requestFactory(
            IntegrationProperties integrationProperties
    ) {
        Duration connect = integrationProperties.httpConnectTimeout() == null
                ? Duration.ofSeconds(3)
                : integrationProperties.httpConnectTimeout();
        Duration read = integrationProperties.httpReadTimeout() == null
                ? Duration.ofSeconds(10)
                : integrationProperties.httpReadTimeout();
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(connect)
                .build();
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(read);
        return factory;
    }
}
