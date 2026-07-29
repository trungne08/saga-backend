package com.saga.be.integration.security;

import com.saga.be.config.IntegrationProperties;
import com.saga.be.exception.IntegrationException;
import com.saga.be.security.SagaPrincipal;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class OAuthStateService {

    private static final String SESSION_ATTRIBUTE =
            OAuthStateService.class.getName() + ".states";
    private static final int STATE_BYTES = 32;

    private final SecureRandom secureRandom;
    private final Clock clock;
    private final Duration ttl;

    @Autowired
    public OAuthStateService(IntegrationProperties properties) {
        this(new SecureRandom(), Clock.systemUTC(), properties.oauthStateTtl());
    }

    OAuthStateService(SecureRandom secureRandom, Clock clock, Duration ttl) {
        this.secureRandom = secureRandom;
        this.clock = clock;
        this.ttl = ttl == null ? Duration.ofMinutes(10) : ttl;
    }

    public String issue(
            HttpSession session,
            SagaPrincipal principal,
            OAuthFlow flow,
            UUID targetId
    ) {
        byte[] random = new byte[STATE_BYTES];
        secureRandom.nextBytes(random);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String stateHash = sha256(state);

        Map<String, StateBinding> bindings = stateBindings(session);
        bindings.entrySet().removeIf(
                entry -> !entry.getValue().expiresAt().isAfter(clock.instant())
        );
        bindings.put(stateHash, new StateBinding(
                principal.cognitoSub(),
                principal.localProfileId(),
                flow,
                targetId,
                clock.instant().plus(ttl)
        ));
        session.setAttribute(SESSION_ATTRIBUTE, bindings);
        return state;
    }

    public StateBinding consume(
            HttpSession session,
            SagaPrincipal principal,
            OAuthFlow expectedFlow,
            UUID expectedTargetId,
            String state
    ) {
        StateBinding binding = consumeBoundState(
                session,
                principal,
                state
        );
        if (
            binding.flow() != expectedFlow
            || !java.util.Objects.equals(binding.targetId(), expectedTargetId)
        ) {
            throw invalidState();
        }
        return binding;
    }

    public StateBinding consumeWithResolvedTarget(
            HttpSession session,
            SagaPrincipal principal,
            OAuthFlow expectedFlow,
            String state
    ) {
        StateBinding binding = consumeBoundState(
                session,
                principal,
                state
        );
        if (binding.flow() != expectedFlow || binding.targetId() == null) {
            throw invalidState();
        }
        return binding;
    }

    public StateBinding consumeAndResolve(
            HttpSession session,
            SagaPrincipal principal,
            String state
    ) {
        return consumeBoundState(session, principal, state);
    }

    private StateBinding consumeBoundState(
            HttpSession session,
            SagaPrincipal principal,
            String state
    ) {
        if (state == null || state.isBlank()) {
            throw invalidState();
        }

        Map<String, StateBinding> bindings = stateBindings(session);
        StateBinding binding = bindings.remove(sha256(state));
        session.setAttribute(SESSION_ATTRIBUTE, bindings);

        if (
            binding == null
            || !binding.expiresAt().isAfter(clock.instant())
            || !MessageDigest.isEqual(
                    binding.cognitoSub().getBytes(StandardCharsets.UTF_8),
                    principal.cognitoSub().getBytes(StandardCharsets.UTF_8)
            )
            || !java.util.Objects.equals(
                    binding.localProfileId(),
                    principal.localProfileId()
            )
        ) {
            throw invalidState();
        }
        return binding;
    }

    @SuppressWarnings("unchecked")
    private Map<String, StateBinding> stateBindings(HttpSession session) {
        Object existing = session.getAttribute(SESSION_ATTRIBUTE);
        if (existing instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, StateBinding>) map);
        }
        return new HashMap<>();
    }

    private String sha256(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private IntegrationException invalidState() {
        return IntegrationException.invalid(
                "OAUTH_STATE_INVALID",
                "The integration authorization state is invalid or expired"
        );
    }

    public record StateBinding(
            String cognitoSub,
            UUID localProfileId,
            OAuthFlow flow,
            UUID targetId,
            Instant expiresAt
    ) implements Serializable {
    }
}
