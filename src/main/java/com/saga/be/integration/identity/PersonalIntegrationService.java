package com.saga.be.integration.identity;

import com.saga.be.config.GitHubIntegrationProperties;
import com.saga.be.config.JiraIntegrationProperties;
import com.saga.be.dto.response.IdentityConnectionResponse;
import com.saga.be.dto.response.IntegrationCallbackResultResponse;
import com.saga.be.dto.response.PersonalIntegrationsResponse;
import com.saga.be.entity.IdentityMap;
import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.GitHubUserIdentity;
import com.saga.be.integration.provider.JiraAccessibleResource;
import com.saga.be.integration.provider.JiraOAuthToken;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.integration.provider.JiraUserIdentity;
import com.saga.be.integration.security.IntegrationAttemptLimiter;
import com.saga.be.integration.security.OAuthFlow;
import com.saga.be.integration.security.OAuthStateService;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.AuthenticationAuditService;
import com.saga.be.service.PersonalIntegrationNotificationProducer;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PersonalIntegrationService {

    private final IdentityMappingService identityMappingService;
    private final OAuthStateService stateService;
    private final JiraProviderClient jiraClient;
    private final GitHubProviderClient gitHubClient;
    private final JiraIntegrationProperties jiraProperties;
    private final GitHubIntegrationProperties gitHubProperties;
    private final IntegrationAttemptLimiter attemptLimiter;
    private final AuthenticationAuditService auditService;
    private final PersonalIntegrationNotificationProducer notificationProducer;

    public PersonalIntegrationService(
            IdentityMappingService identityMappingService,
            OAuthStateService stateService,
            JiraProviderClient jiraClient,
            GitHubProviderClient gitHubClient,
            JiraIntegrationProperties jiraProperties,
            GitHubIntegrationProperties gitHubProperties,
            IntegrationAttemptLimiter attemptLimiter,
            AuthenticationAuditService auditService,
            PersonalIntegrationNotificationProducer notificationProducer
    ) {
        this.identityMappingService = identityMappingService;
        this.stateService = stateService;
        this.jiraClient = jiraClient;
        this.gitHubClient = gitHubClient;
        this.jiraProperties = jiraProperties;
        this.gitHubProperties = gitHubProperties;
        this.attemptLimiter = attemptLimiter;
        this.auditService = auditService;
        this.notificationProducer = notificationProducer;
    }

    public PersonalIntegrationsResponse connections(SagaPrincipal principal) {
        return identityMappingService.getOwnConnections(principal);
    }

    public URI beginJira(
            SagaPrincipal principal,
            HttpSession session
    ) {
        limit(principal, "personal-jira-connect");
        String state = stateService.issue(
                session,
                principal,
                OAuthFlow.PERSONAL_JIRA,
                null
        );
        return jiraClient.authorizationUri(
                state,
                jiraProperties.callbackUrl()
        );
    }

    public IdentityConnectionResponse completeJiraCallback(
            SagaPrincipal principal,
            String code,
            String oauthError,
            String remoteAddress
    ) {
        limit(principal, "personal-jira-callback");
        requireConsent(code, oauthError);

        JiraOAuthToken token = jiraClient.exchangeCode(
                code,
                jiraProperties.callbackUrl()
        );
        List<JiraAccessibleResource> resources =
                jiraClient.accessibleResources(token.accessToken());
        if (resources.isEmpty()) {
            throw IntegrationException.conflict(
                    "JIRA_SITE_ACCESS_MISSING",
                    "No accessible Jira Cloud site was returned"
            );
        }
        JiraUserIdentity user = jiraClient.currentUser(
                token.accessToken(),
                resources.get(0).cloudId()
        );
        IdentityMap mapping = identityMappingService.connectVerified(
                principal,
                IntegrationProvider.JIRA,
                user.accountId(),
                user.displayName(),
                user.email()
        );
        auditService.recordIntegrationEvent(
                principal,
                "PERSONAL_IDENTITY_CONNECTED",
                "JIRA_IDENTITY",
                mapping.getId(),
                "SUCCESS",
                remoteAddress
        );
        notificationProducer.jiraLinked(principal, mapping.getId());
        return IdentityConnectionResponse.from(mapping);
    }

    public URI beginGitHub(
            SagaPrincipal principal,
            HttpSession session
    ) {
        limit(principal, "personal-github-connect");
        String state = stateService.issue(
                session,
                principal,
                OAuthFlow.PERSONAL_GITHUB,
                null
        );
        return gitHubClient.userAuthorizationUri(
                state,
                gitHubProperties.personalCallbackUrl()
        );
    }

    public IdentityConnectionResponse finishGitHub(
            SagaPrincipal principal,
            HttpSession session,
            String state,
            String code,
            String oauthError,
            String remoteAddress
    ) {
        limit(principal, "personal-github-callback");
        stateService.consume(
                session,
                principal,
                OAuthFlow.PERSONAL_GITHUB,
                null,
                state
        );
        return finishGitHubAfterState(
                principal,
                session,
                code,
                oauthError,
                remoteAddress
        );
    }

    public IntegrationCallbackResultResponse finishGitHubCallback(
            SagaPrincipal principal,
            HttpSession session,
            String state,
            String code,
            String oauthError,
            String remoteAddress
    ) {
        limit(principal, "personal-github-callback");
        stateService.consume(
                session,
                principal,
                OAuthFlow.PERSONAL_GITHUB,
                null,
                state
        );
        try {
            return IntegrationCallbackResultResponse.personalSuccess(
                    finishGitHubAfterState(
                            principal,
                            session,
                            code,
                            oauthError,
                            remoteAddress
                    )
            );
        } catch (IntegrationException exception) {
            return IntegrationCallbackResultResponse.failure(
                    IntegrationProvider.GITHUB,
                    com.saga.be.dto.response.IntegrationCallbackFlow.PERSONAL,
                    null,
                    exception.getCode(),
                    exception.getMessage()
            );
        }
    }

    private IdentityConnectionResponse finishGitHubAfterState(
            SagaPrincipal principal,
            HttpSession session,
            String code,
            String oauthError,
            String remoteAddress
    ) {
        requireConsent(code, oauthError);

        String userToken = gitHubClient.exchangeUserCode(
                code,
                gitHubProperties.personalCallbackUrl()
        );
        GitHubUserIdentity user = gitHubClient.currentUser(userToken);
        IdentityMap mapping = identityMappingService.connectVerified(
                principal,
                IntegrationProvider.GITHUB,
                Long.toString(user.id()),
                user.login(),
                user.email()
        );
        auditService.recordIntegrationEvent(
                principal,
                "PERSONAL_IDENTITY_CONNECTED",
                "GITHUB_IDENTITY",
                mapping.getId(),
                "SUCCESS",
                remoteAddress
        );
        notificationProducer.gitHubLinked(principal, mapping.getId());
        return IdentityConnectionResponse.from(mapping);
    }

    public void disconnect(
            SagaPrincipal principal,
            IntegrationProvider provider,
            String remoteAddress
    ) {
        identityMappingService.disconnectOwn(principal, provider);
        auditService.recordIntegrationEvent(
                principal,
                "PERSONAL_IDENTITY_DISCONNECTED",
                provider.name() + "_IDENTITY",
                principal.localProfileId(),
                "SUCCESS",
                remoteAddress
        );
    }

    private void requireConsent(String code, String oauthError) {
        if (oauthError != null || code == null || code.isBlank()) {
            throw IntegrationException.invalid(
                    "OAUTH_CONSENT_DENIED",
                    "Provider authorization was denied or cancelled"
            );
        }
    }

    private void limit(SagaPrincipal principal, String operation) {
        attemptLimiter.requireAllowed(principal.cognitoSub() + ":" + operation);
    }
}
