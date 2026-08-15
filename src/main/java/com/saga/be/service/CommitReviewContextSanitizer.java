package com.saga.be.service;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class CommitReviewContextSanitizer {

    private static final String REDACTION = "[REDACTED_SECRET]";
    private static final List<Pattern> CREDENTIAL_PATTERNS = List.of(
            Pattern.compile("-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{20,}\\b"),
            Pattern.compile("\\bAIza[0-9A-Za-z_-]{20,}\\b"),
            Pattern.compile("\\b(?:gh[pousr]_|github_pat_)[A-Za-z0-9_]{20,}\\b"),
            Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"),
            Pattern.compile("\\b(?:Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]{16,}", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b"),
            Pattern.compile("\\b(?:jdbc:mysql|mysql|mongodb(?:\\+srv)?|postgres(?:ql)?)://[^/\\s:@]+:[^@\\s/]+@", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:SAGA_AI_SERVICE_TOKEN|GITHUB_PRIVATE_KEY|GITHUB_CLIENT_SECRET|GITHUB_WEBHOOK_SECRET|JIRA_CLIENT_SECRET|INTEGRATION_TOKEN_ENCRYPTION_KEY|DATABASE_PASSWORD|AIVEN_DB_PASSWORD)\\s*[:=]\\s*['\"]?[^\\s'\"]{8,}", Pattern.CASE_INSENSITIVE)
    );
    private static final List<String> SECRET_ENV_NAMES = List.of(
            "SAGA_AI_SERVICE_TOKEN",
            "GITHUB_PRIVATE_KEY",
            "GITHUB_CLIENT_SECRET",
            "GITHUB_WEBHOOK_SECRET",
            "JIRA_CLIENT_SECRET",
            "INTEGRATION_TOKEN_ENCRYPTION_KEY",
            "DATABASE_PASSWORD",
            "AIVEN_DB_PASSWORD",
            "DATABASE_JDBC_URL",
            "AIVEN_JDBC_URL"
    );

    private final List<String> configuredSecrets;

    public CommitReviewContextSanitizer(Environment environment) {
        this.configuredSecrets = SECRET_ENV_NAMES.stream()
                .map(environment::getProperty)
                .filter(value -> value != null && value.length() >= 8)
                .toList();
    }

    public SanitizedText sanitize(String source) {
        if (source == null) {
            return new SanitizedText(null, false);
        }
        String value = source;
        boolean redacted = false;
        for (Pattern pattern : CREDENTIAL_PATTERNS) {
            String next = pattern.matcher(value).replaceAll(REDACTION);
            redacted = redacted || !next.equals(value);
            value = next;
        }
        for (String secret : configuredSecrets) {
            if (value.contains(secret)) {
                value = value.replace(secret, REDACTION);
                redacted = true;
            }
        }
        return new SanitizedText(value, redacted);
    }

    public record SanitizedText(String value, boolean redacted) {
    }
}
