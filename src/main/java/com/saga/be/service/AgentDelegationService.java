package com.saga.be.service;

import com.saga.be.config.AgentAiProperties;
import com.saga.be.entity.AiAgentDelegationContext;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.AiAgentDelegationContextRepository;
import com.saga.be.security.SagaPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentDelegationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final AiAgentDelegationContextRepository contexts;
    private final DelegatedActorResolver actors;
    private final CurrentAccountStatusService accountStatuses;
    private final AgentAiProperties properties;

    public AgentDelegationService(
            AiAgentDelegationContextRepository contexts,
            DelegatedActorResolver actors,
            CurrentAccountStatusService accountStatuses,
            AgentAiProperties properties
    ) {
        this.contexts = contexts;
        this.actors = actors;
        this.accountStatuses = accountStatuses;
        this.properties = properties;
    }

    @Transactional
    public String issue(SagaPrincipal actor, UUID conversationId) {
        return issue(actor, conversationId, null);
    }

    @Transactional
    public String issue(SagaPrincipal actor, UUID conversationId, UUID courseId) {
        if (actor == null || actor.localProfileId() == null || conversationId == null
                || !accountStatuses.isAllowedForBusinessApi(actor)) {
            throw new AccessDeniedException("An active authenticated actor is required");
        }
        byte[] raw = new byte[32];
        SECURE_RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        AiAgentDelegationContext context = new AiAgentDelegationContext();
        context.setTokenHash(hash(token));
        context.setConversationId(conversationId);
        context.setActorCognitoSub(actor.cognitoSub());
        context.setActorProfileId(actor.localProfileId());
        context.setActorApplicationRole(actor.applicationRole());
        context.setCapabilities(EnumSet.allOf(AgentDelegationCapability.class).stream()
                .map(Enum::name).sorted().collect(Collectors.joining(",")));
        context.setExpiresAt(LocalDateTime.now().plus(ttl()));
        context.setCourseId(courseId);
        contexts.saveAndFlush(context);
        return token;
    }

    @Transactional(readOnly = true)
    public SagaPrincipal resolve(
            String token,
            UUID conversationId,
            AgentDelegationCapability capability
    ) {
        return resolveAccess(token, conversationId, capability).actor();
    }

    @Transactional(readOnly = true)
    public AgentDelegatedAccess resolveAccess(
            String token,
            UUID conversationId,
            AgentDelegationCapability capability
    ) {
        if (token == null || token.length() < 32 || conversationId == null || capability == null) {
            throw invalid("AGENT_CONTEXT_INVALID");
        }
        AiAgentDelegationContext context = contexts.findByTokenHash(hash(token))
                .orElseThrow(() -> invalid("AGENT_CONTEXT_INVALID"));
        if (!conversationId.equals(context.getConversationId())) {
            throw invalid("AGENT_CONTEXT_CONVERSATION_MISMATCH");
        }
        if (!capabilities(context).contains(capability)) {
            throw new IntegrationException(
                    HttpStatus.FORBIDDEN,
                    "AGENT_CONTEXT_CAPABILITY_FORBIDDEN",
                    "The delegated agent context does not permit this capability"
            );
        }
        if (!context.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw invalid("AGENT_CONTEXT_EXPIRED");
        }
        return new AgentDelegatedAccess(
                actors.resolve(
                        context.getActorCognitoSub(),
                        context.getActorProfileId(),
                        context.getActorApplicationRole()
                ),
                context.getCourseId()
        );
    }

    private Set<AgentDelegationCapability> capabilities(AiAgentDelegationContext context) {
        try {
            return java.util.Arrays.stream(context.getCapabilities().split(","))
                    .map(AgentDelegationCapability::valueOf)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (RuntimeException exception) {
            throw invalid("AGENT_CONTEXT_INVALID");
        }
    }

    private Duration ttl() {
        Duration configured = properties.delegationTtl();
        if (configured == null) {
            return DEFAULT_TTL;
        }
        if (configured.isNegative() || configured.isZero()
                || configured.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalStateException("Agent delegation TTL must be in (0, PT15M]");
        }
        return configured;
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private IntegrationException invalid(String code) {
        return new IntegrationException(
                HttpStatus.UNAUTHORIZED, code, "The delegated agent context is invalid"
        );
    }
}
