package com.saga.be.integration.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.config.GitHubIntegrationProperties;
import com.saga.be.config.JiraIntegrationProperties;
import com.saga.be.entity.IdentityMap;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.IdentityMappingStatus;
import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.GitHubUserIdentity;
import com.saga.be.integration.provider.JiraAccessibleResource;
import com.saga.be.integration.provider.JiraOAuthToken;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.integration.provider.JiraUserIdentity;
import com.saga.be.integration.security.IntegrationAttemptLimiter;
import com.saga.be.integration.security.OAuthFlow;
import com.saga.be.integration.security.OAuthStateService;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.AuthenticationAuditService;
import com.saga.be.service.PersonalIntegrationNotificationProducer;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class PersonalIntegrationServiceTest {

    private IdentityMappingService mappingService;
    private OAuthStateService stateService;
    private JiraProviderClient jiraClient;
    private GitHubProviderClient gitHubClient;
    private PersonalIntegrationNotificationProducer notificationProducer;
    private PersonalIntegrationService service;
    private SagaPrincipal principal;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        mappingService = mock(IdentityMappingService.class);
        stateService = mock(OAuthStateService.class);
        jiraClient = mock(JiraProviderClient.class);
        gitHubClient = mock(GitHubProviderClient.class);
        notificationProducer = mock(PersonalIntegrationNotificationProducer.class);
        service = new PersonalIntegrationService(
                mappingService,
                stateService,
                jiraClient,
                gitHubClient,
                new JiraIntegrationProperties(
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "https://saga.example/api/integrations/jira/callback",
                        null,
                        null
                ),
                new GitHubIntegrationProperties(
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "https://api.github.com",
                        "https://github.com",
                        "https://saga.example/api/me/integrations/github/callback",
                        "https://saga.example/api/integrations/github/project/callback",
                        "https://saga.example/api/webhooks/github"
                ),
                mock(IntegrationAttemptLimiter.class),
                mock(AuthenticationAuditService.class),
                notificationProducer
        );
        principal = new SagaPrincipal(
                "student-sub",
                "student@example.com",
                "Student",
                ApplicationRole.STUDENT,
                UUID.randomUUID(),
                AccountStatus.ACTIVE
        );
        session = new MockHttpSession();
    }

    @Test
    void jiraCallbackMapsProviderAccountIdNotDisplayName() {
        JiraOAuthToken token = new JiraOAuthToken(
                "short-lived-access-token",
                null,
                Instant.now().plusSeconds(3600),
                Set.of("read:me")
        );
        when(jiraClient.exchangeCode(any(), any())).thenReturn(token);
        when(jiraClient.accessibleResources(token.accessToken())).thenReturn(
                List.of(new JiraAccessibleResource(
                        "cloud-id",
                        "Site",
                        "https://site.atlassian.net"
                ))
        );
        when(jiraClient.currentUser(token.accessToken(), "cloud-id"))
                .thenReturn(new JiraUserIdentity(
                        "stable-atlassian-account-id",
                        "Mutable Display Name",
                        "jira@example.com"
                ));
        when(mappingService.connectVerified(
                eq(principal),
                eq(IntegrationProvider.JIRA),
                eq("stable-atlassian-account-id"),
                eq("Mutable Display Name"),
                eq("jira@example.com")
        )).thenReturn(mapping(IntegrationProvider.JIRA));

        service.completeJiraCallback(
                principal,
                "code",
                null,
                "127.0.0.1"
        );

        verify(mappingService).connectVerified(
                principal,
                IntegrationProvider.JIRA,
                "stable-atlassian-account-id",
                "Mutable Display Name",
                "jira@example.com"
        );
        verify(notificationProducer).jiraLinked(eq(principal), any());
    }

    @Test
    void githubCallbackMapsNumericUserIdNotLoginOrEmail() {
        when(gitHubClient.exchangeUserCode(
                "code",
                "https://saga.example/api/me/integrations/github/callback"
        ))
                .thenReturn("short-lived-user-token");
        when(gitHubClient.currentUser("short-lived-user-token"))
                .thenReturn(new GitHubUserIdentity(
                        987654321L,
                        "mutable-login",
                        "Mutable Name",
                        "github@example.com"
                ));
        when(mappingService.connectVerified(
                eq(principal),
                eq(IntegrationProvider.GITHUB),
                eq("987654321"),
                eq("mutable-login"),
                eq("github@example.com")
        )).thenReturn(mapping(IntegrationProvider.GITHUB));

        var response = service.finishGitHub(
                principal,
                session,
                "state",
                "code",
                null,
                "127.0.0.1"
        );

        assertEquals(IntegrationProvider.GITHUB, response.provider());
        verify(stateService).consume(
                session,
                principal,
                OAuthFlow.PERSONAL_GITHUB,
                null,
                "state"
        );
        verify(mappingService).connectVerified(
                principal,
                IntegrationProvider.GITHUB,
                "987654321",
                "mutable-login",
                "github@example.com"
        );
        verify(notificationProducer).gitHubLinked(eq(principal), any());
    }

    private IdentityMap mapping(IntegrationProvider provider) {
        IdentityMap mapping = IdentityMap.builder()
                .provider(provider)
                .mappingStatus(IdentityMappingStatus.ACTIVE)
                .build();
        mapping.setId(UUID.randomUUID());
        return mapping;
    }
}
