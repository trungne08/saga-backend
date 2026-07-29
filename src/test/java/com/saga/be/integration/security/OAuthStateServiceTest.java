package com.saga.be.integration.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.exception.IntegrationException;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class OAuthStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

    @Test
    void stateIsBoundToSessionPrincipalFlowAndTarget() {
        OAuthStateService service = serviceAt(NOW);
        MockHttpSession session = new MockHttpSession();
        SagaPrincipal principal = student(UUID.randomUUID(), "student-sub");
        UUID targetId = UUID.randomUUID();

        String state = service.issue(
                session,
                principal,
                OAuthFlow.PROJECT_JIRA,
                targetId
        );

        OAuthStateService.StateBinding binding = service.consume(
                session,
                principal,
                OAuthFlow.PROJECT_JIRA,
                targetId,
                state
        );
        assertEquals("student-sub", binding.cognitoSub());
        assertEquals(targetId, binding.targetId());
    }

    @Test
    void stateCannotBeReplayed() {
        OAuthStateService service = serviceAt(NOW);
        MockHttpSession session = new MockHttpSession();
        SagaPrincipal principal = student(UUID.randomUUID(), "student-sub");
        String state = service.issue(
                session,
                principal,
                OAuthFlow.PERSONAL_GITHUB,
                null
        );

        service.consume(
                session,
                principal,
                OAuthFlow.PERSONAL_GITHUB,
                null,
                state
        );

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> service.consume(
                        session,
                        principal,
                        OAuthFlow.PERSONAL_GITHUB,
                        null,
                        state
                )
        );
        assertEquals("OAUTH_STATE_INVALID", exception.getCode());
    }

    @Test
    void stateCannotBeUsedByAnotherAuthenticatedStudent() {
        OAuthStateService service = serviceAt(NOW);
        MockHttpSession session = new MockHttpSession();
        SagaPrincipal owner = student(UUID.randomUUID(), "owner-sub");
        String state = service.issue(
                session,
                owner,
                OAuthFlow.PERSONAL_JIRA,
                null
        );

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> service.consume(
                        session,
                        student(UUID.randomUUID(), "attacker-sub"),
                        OAuthFlow.PERSONAL_JIRA,
                        null,
                        state
                )
        );
        assertEquals("OAUTH_STATE_INVALID", exception.getCode());
    }

    @Test
    void expiredStateIsRejected() {
        MockHttpSession session = new MockHttpSession();
        SagaPrincipal principal = student(UUID.randomUUID(), "student-sub");
        UUID targetId = UUID.randomUUID();
        String state = serviceAt(NOW).issue(
                session,
                principal,
                OAuthFlow.PROJECT_GITHUB_INSTALLATION,
                targetId
        );

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> serviceAt(NOW.plus(Duration.ofMinutes(11))).consume(
                        session,
                        principal,
                        OAuthFlow.PROJECT_GITHUB_INSTALLATION,
                        targetId,
                        state
                )
        );
        assertEquals("OAUTH_STATE_INVALID", exception.getCode());
    }

    @Test
    void stateIsExpiredAtTheExactTtlBoundary() {
        MockHttpSession session = new MockHttpSession();
        SagaPrincipal principal = student(UUID.randomUUID(), "student-sub");
        String state = serviceAt(NOW).issue(
                session,
                principal,
                OAuthFlow.PERSONAL_JIRA,
                null
        );

        assertThrows(
                IntegrationException.class,
                () -> serviceAt(NOW.plus(Duration.ofMinutes(10))).consume(
                        session,
                        principal,
                        OAuthFlow.PERSONAL_JIRA,
                        null,
                        state
                )
        );
    }

    @Test
    void stateCannotCrossPersonalAndProjectFlows() {
        OAuthStateService service = serviceAt(NOW);
        MockHttpSession session = new MockHttpSession();
        SagaPrincipal principal = student(UUID.randomUUID(), "student-sub");
        String state = service.issue(
                session,
                principal,
                OAuthFlow.PERSONAL_JIRA,
                null
        );

        assertThrows(
                IntegrationException.class,
                () -> service.consume(
                        session,
                        principal,
                        OAuthFlow.PROJECT_JIRA,
                        null,
                        state
                )
        );
    }

    private OAuthStateService serviceAt(Instant instant) {
        return new OAuthStateService(
                new SecureRandom(),
                Clock.fixed(instant, ZoneOffset.UTC),
                Duration.ofMinutes(10)
        );
    }

    private SagaPrincipal student(UUID id, String subject) {
        return new SagaPrincipal(
                subject,
                "student@example.com",
                "Student",
                ApplicationRole.STUDENT,
                id,
                AccountStatus.ACTIVE
        );
    }
}
