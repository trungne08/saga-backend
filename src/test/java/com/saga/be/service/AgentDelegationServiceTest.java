package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.config.AgentAiProperties;
import com.saga.be.entity.AiAgentDelegationContext;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.AiAgentDelegationContextRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentDelegationServiceTest {

    @Test
    void issuesOnlyOpaqueTokenAndStoresHashBoundToActorConversationAndExpiry() {
        AiAgentDelegationContextRepository repository = mock(AiAgentDelegationContextRepository.class);
        DelegatedActorResolver resolver = mock(DelegatedActorResolver.class);
        CurrentAccountStatusService statuses = mock(CurrentAccountStatusService.class);
        AgentDelegationService service = service(repository, resolver, statuses);
        SagaPrincipal actor = actor();
        UUID conversationId = UUID.randomUUID();
        when(statuses.isAllowedForBusinessApi(actor)).thenReturn(true);

        String token = service.issue(actor, conversationId);

        ArgumentCaptor<AiAgentDelegationContext> captured = ArgumentCaptor.forClass(AiAgentDelegationContext.class);
        verify(repository).saveAndFlush(captured.capture());
        AiAgentDelegationContext stored = captured.getValue();
        assertTrue(token.length() >= 32);
        assertFalse(stored.getTokenHash().contains(token));
        assertTrue(stored.getTokenHash().matches("[0-9a-f]{64}"));
        assertTrue(stored.getExpiresAt().isAfter(LocalDateTime.now()));
        org.junit.jupiter.api.Assertions.assertEquals(conversationId, stored.getConversationId());
        org.junit.jupiter.api.Assertions.assertEquals(actor.localProfileId(), stored.getActorProfileId());
        assertTrue(stored.getCapabilities().contains("READ"));
        assertTrue(stored.getCapabilities().contains("PROPOSE_WRITE"));
    }

    @Test
    void resolveRejectsExpiredAndConversationMismatchAndUsesCurrentActorResolver() {
        AiAgentDelegationContextRepository repository = mock(AiAgentDelegationContextRepository.class);
        DelegatedActorResolver resolver = mock(DelegatedActorResolver.class);
        CurrentAccountStatusService statuses = mock(CurrentAccountStatusService.class);
        AgentDelegationService service = service(repository, resolver, statuses);
        UUID conversationId = UUID.randomUUID();
        AiAgentDelegationContext context = context(conversationId, LocalDateTime.now().plusMinutes(1));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(context));
        when(resolver.resolve(anyString(), any(), any())).thenReturn(actor());

        SagaPrincipal resolved = service.resolve(
                "opaque-token-value-that-is-long-enough-123",
                conversationId,
                AgentDelegationCapability.READ
        );
        org.junit.jupiter.api.Assertions.assertEquals(actor().localProfileId(), resolved.localProfileId());
        verify(resolver).resolve(
                context.getActorCognitoSub(), context.getActorProfileId(), context.getActorApplicationRole()
        );

        assertThrows(IntegrationException.class, () -> service.resolve(
                "opaque-token-value-that-is-long-enough-123",
                UUID.randomUUID(),
                AgentDelegationCapability.READ
        ));

        context.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThrows(IntegrationException.class, () -> service.resolve(
                "opaque-token-value-that-is-long-enough-123",
                conversationId,
                AgentDelegationCapability.READ
        ));
    }

    private AgentDelegationService service(
            AiAgentDelegationContextRepository repository,
            DelegatedActorResolver resolver,
            CurrentAccountStatusService statuses
    ) {
        return new AgentDelegationService(
                repository, resolver, statuses,
                new AgentAiProperties(null, null, Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofMinutes(5))
        );
    }

    private AiAgentDelegationContext context(UUID conversationId, LocalDateTime expiresAt) {
        AiAgentDelegationContext value = new AiAgentDelegationContext();
        value.setTokenHash("a".repeat(64));
        value.setConversationId(conversationId);
        value.setActorCognitoSub("student-sub");
        value.setActorProfileId(actor().localProfileId());
        value.setActorApplicationRole(ApplicationRole.STUDENT);
        value.setCapabilities("PROPOSE_WRITE,READ");
        value.setExpiresAt(expiresAt);
        return value;
    }

    private SagaPrincipal actor() {
        return new SagaPrincipal(
                "student-sub", "student@example.test", "Student",
                ApplicationRole.STUDENT,
                UUID.fromString("00000000-0000-0000-0000-000000000111"),
                AccountStatus.ACTIVE
        );
    }
}

