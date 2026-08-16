package com.saga.be.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.service.CurrentAccountStatusService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

@ExtendWith(MockitoExtension.class)
class AccountSessionEventHubTest {

    @Mock
    private CurrentAccountStatusService accountStatusService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-17T04:11:00Z"), ZoneOffset.UTC);
    private AccountSessionEventHub hub;

    @AfterEach
    void tearDown() {
        if (hub != null) {
            hub.completeAllAndClear();
        }
    }

    @Test
    void accountDisabledPayloadContainsOnlyTheSafeBrowserCodeAndTimestamp() {
        hub = newHub();
        String payload = hub.accountDisabledPayload();
        assertEquals("{\"code\":\"ACCOUNT_DISABLED\",\"occurredAt\":\"2026-08-17T04:11:00Z\"}", payload);
        assertFalse(payload.contains("jsessionid"));
        assertFalse(payload.contains("email"));
        assertFalse(payload.contains("INACTIVE"));
        assertFalse(payload.contains("SUSPENDED"));
    }

    @Test
    void subscribeThenCompleteAllRemovesTheEmitterFromTheRegistry() {
        hub = newHub();
        SagaPrincipal student = principal(ApplicationRole.STUDENT);
        MockHttpSession session = new MockHttpSession();

        hub.subscribe(student, session);
        assertEquals(1, hub.activeSubscriptionCount());
        assertEquals(1, hub.activeSubscriptionCount(ApplicationRole.STUDENT, student.localProfileId()));

        hub.completeAllAndClear();
        assertEquals(0, hub.activeSubscriptionCount());
        assertFalse(session.isInvalid());
    }

    @Test
    void revokeInvalidatesEverySessionForTheProfileAndDoesNotTouchOtherProfiles() {
        hub = newHub();
        SagaPrincipal student = principal(ApplicationRole.STUDENT);
        SagaPrincipal other = principal(ApplicationRole.STUDENT);
        MockHttpSession first = new MockHttpSession();
        MockHttpSession second = new MockHttpSession();
        MockHttpSession otherSession = new MockHttpSession();
        hub.subscribe(student, first);
        hub.subscribe(student, second);
        hub.subscribe(other, otherSession);

        hub.revoke(ApplicationRole.STUDENT, student.localProfileId());

        assertTrue(first.isInvalid());
        assertTrue(second.isInvalid());
        assertFalse(otherSession.isInvalid());
        assertEquals(0, hub.activeSubscriptionCount(ApplicationRole.STUDENT, student.localProfileId()));
        assertEquals(1, hub.activeSubscriptionCount(ApplicationRole.STUDENT, other.localProfileId()));
    }

    @Test
    void revalidationRevokesDisabledProfilesAndLeavesActiveOnes() {
        hub = newHub();
        SagaPrincipal active = principal(ApplicationRole.STUDENT);
        SagaPrincipal disabled = principal(ApplicationRole.LECTURER);
        MockHttpSession activeSession = new MockHttpSession();
        MockHttpSession disabledSession = new MockHttpSession();
        hub.subscribe(active, activeSession);
        hub.subscribe(disabled, disabledSession);
        when(accountStatusService.findDisabledIds(eq(ApplicationRole.STUDENT), any()))
                .thenReturn(Set.of());
        when(accountStatusService.findDisabledIds(eq(ApplicationRole.LECTURER), any()))
                .thenReturn(Set.of(disabled.localProfileId()));

        hub.revalidateConnectedProfiles();

        assertFalse(activeSession.isInvalid());
        assertTrue(disabledSession.isInvalid());
        assertEquals(1, hub.activeSubscriptionCount());
        assertEquals(1, hub.activeSubscriptionCount(ApplicationRole.STUDENT, active.localProfileId()));
    }

    @Test
    void heartbeatOnALiveEmitterDoesNotInvalidateTheSession() {
        hub = newHub();
        SagaPrincipal student = principal(ApplicationRole.STUDENT);
        MockHttpSession session = new MockHttpSession();
        hub.subscribe(student, session);

        hub.sendHeartbeats();

        assertFalse(session.isInvalid());
        assertEquals(1, hub.activeSubscriptionCount());
    }

    @Test
    void adminSubscriptionsAreHeartbeatOnlyAndAreNotRevoked() {
        hub = newHub();
        SagaPrincipal admin = principal(ApplicationRole.ADMIN);
        MockHttpSession session = new MockHttpSession();
        hub.subscribe(admin, session);

        hub.revoke(ApplicationRole.ADMIN, admin.localProfileId());
        hub.revalidateConnectedProfiles();

        verify(accountStatusService, never()).findDisabledIds(eq(ApplicationRole.ADMIN), any());
        assertFalse(session.isInvalid());
        assertEquals(1, hub.activeSubscriptionCount(ApplicationRole.ADMIN, admin.localProfileId()));
    }

    @Test
    void completeAllAndClearDoesNotGrowTheRegistry() {
        hub = newHub();
        hub.subscribe(principal(ApplicationRole.STUDENT), new MockHttpSession());
        hub.subscribe(principal(ApplicationRole.LECTURER), new MockHttpSession());
        hub.completeAllAndClear();
        assertEquals(0, hub.activeSubscriptionCount());
        hub.completeAllAndClear();
        assertEquals(0, hub.activeSubscriptionCount());
    }

    @Test
    void schedulerSweepLeavesActiveSubscriptionsUntilExplicitCleanup() {
        hub = newHub();
        AccountSessionRevalidationScheduler scheduler = new AccountSessionRevalidationScheduler(hub);
        SagaPrincipal student = principal(ApplicationRole.STUDENT);
        hub.subscribe(student, new MockHttpSession());
        when(accountStatusService.findDisabledIds(eq(ApplicationRole.STUDENT), any())).thenReturn(Set.of());

        scheduler.revalidateAndHeartbeat();

        assertEquals(1, hub.activeSubscriptionCount());
        hub.completeAllAndClear();
        assertEquals(0, hub.activeSubscriptionCount());
    }

    private AccountSessionEventHub newHub() {
        return new AccountSessionEventHub(accountStatusService, clock, 1_800_000L);
    }

    private SagaPrincipal principal(ApplicationRole role) {
        return new SagaPrincipal(
                role.name().toLowerCase() + "-sub",
                role.name().toLowerCase() + "@test",
                role.name(),
                role,
                UUID.randomUUID(),
                role == ApplicationRole.ADMIN ? null : AccountStatus.ACTIVE
        );
    }
}
