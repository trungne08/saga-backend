package com.saga.be.integration.project;

import com.saga.be.config.IntegrationProperties;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.provider.JiraAccessibleResource;
import com.saga.be.integration.provider.JiraOAuthToken;
import com.saga.be.integration.security.IntegrationSecretCipher;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProjectIntegrationSessionStore {

    private static final String SESSION_ATTRIBUTE =
            ProjectIntegrationSessionStore.class.getName() + ".jiraGrants";
    private static final String GITHUB_SESSION_ATTRIBUTE =
            ProjectIntegrationSessionStore.class.getName()
                    + ".githubInstallations";

    private final IntegrationSecretCipher cipher;
    private final Duration ttl;

    public ProjectIntegrationSessionStore(
            IntegrationSecretCipher cipher,
            IntegrationProperties properties
    ) {
        this.cipher = cipher;
        this.ttl = properties.oauthStateTtl() == null
                ? Duration.ofMinutes(10)
                : properties.oauthStateTtl();
    }

    public void putJiraGrant(
            HttpSession session,
            UUID projectId,
            JiraOAuthToken token,
            List<JiraAccessibleResource> resources
    ) {
        var grants = grants(session);
        grants.put(projectId, new PendingJiraGrant(
                cipher.encrypt(
                        token.accessToken(),
                        purpose(projectId, "pending-access")
                ),
                cipher.encrypt(
                        token.refreshToken(),
                        purpose(projectId, "pending-refresh")
                ),
                token.expiresAt(),
                token.scopes(),
                List.copyOf(resources),
                Instant.now().plus(ttl)
        ));
        session.setAttribute(SESSION_ATTRIBUTE, grants);
    }

    public ResolvedJiraGrant requireJiraGrant(
            HttpSession session,
            UUID projectId
    ) {
        var grants = grants(session);
        PendingJiraGrant grant = grants.get(projectId);
        if (grant == null || grant.expiresAt().isBefore(Instant.now())) {
            grants.remove(projectId);
            session.setAttribute(SESSION_ATTRIBUTE, grants);
            throw IntegrationException.invalid(
                    "JIRA_AUTHORIZATION_EXPIRED",
                    "Jira authorization is missing or expired"
            );
        }
        return new ResolvedJiraGrant(
                cipher.decrypt(
                        grant.encryptedAccessToken(),
                        purpose(projectId, "pending-access")
                ),
                cipher.decrypt(
                        grant.encryptedRefreshToken(),
                        purpose(projectId, "pending-refresh")
                ),
                grant.tokenExpiresAt(),
                grant.scopes(),
                grant.resources()
        );
    }

    public void removeJiraGrant(HttpSession session, UUID projectId) {
        var grants = grants(session);
        grants.remove(projectId);
        session.setAttribute(SESSION_ATTRIBUTE, grants);
    }

    public void putGitHubInstallation(
            HttpSession session,
            UUID projectId,
            long installationId
    ) {
        var installations = githubInstallations(session);
        installations.put(projectId, new PendingGitHubInstallation(
                installationId,
                Instant.now().plus(ttl)
        ));
        session.setAttribute(GITHUB_SESSION_ATTRIBUTE, installations);
    }

    public long requireGitHubInstallation(
            HttpSession session,
            UUID projectId
    ) {
        var installations = githubInstallations(session);
        PendingGitHubInstallation pending = installations.get(projectId);
        if (
            pending == null
            || pending.expiresAt().isBefore(Instant.now())
        ) {
            installations.remove(projectId);
            session.setAttribute(GITHUB_SESSION_ATTRIBUTE, installations);
            throw IntegrationException.invalid(
                    "GITHUB_INSTALLATION_AUTHORIZATION_EXPIRED",
                    "GitHub installation authorization is missing or expired"
            );
        }
        return pending.installationId();
    }

    public void removeGitHubInstallation(
            HttpSession session,
            UUID projectId
    ) {
        var installations = githubInstallations(session);
        installations.remove(projectId);
        session.setAttribute(GITHUB_SESSION_ATTRIBUTE, installations);
    }

    @SuppressWarnings("unchecked")
    private HashMap<UUID, PendingJiraGrant> grants(HttpSession session) {
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        if (value instanceof java.util.Map<?, ?> map) {
            return new HashMap<>((java.util.Map<UUID, PendingJiraGrant>) map);
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private HashMap<UUID, PendingGitHubInstallation> githubInstallations(
            HttpSession session
    ) {
        Object value = session.getAttribute(GITHUB_SESSION_ATTRIBUTE);
        if (value instanceof java.util.Map<?, ?> map) {
            return new HashMap<>(
                    (java.util.Map<UUID, PendingGitHubInstallation>) map
            );
        }
        return new HashMap<>();
    }

    private String purpose(UUID projectId, String value) {
        return "jira:" + projectId + ":" + value;
    }

    private record PendingJiraGrant(
            String encryptedAccessToken,
            String encryptedRefreshToken,
            Instant tokenExpiresAt,
            Set<String> scopes,
            List<JiraAccessibleResource> resources,
            Instant expiresAt
    ) implements Serializable {
    }

    private record PendingGitHubInstallation(
            long installationId,
            Instant expiresAt
    ) implements Serializable {
    }

    public record ResolvedJiraGrant(
            String accessToken,
            String refreshToken,
            Instant tokenExpiresAt,
            Set<String> scopes,
            List<JiraAccessibleResource> resources
    ) {
    }
}
