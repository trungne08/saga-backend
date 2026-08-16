package com.saga.be.security;

import com.saga.be.service.CurrentAccountStatusService;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Process-local SSE registry for account-status revocation.
 * DB AccountStatus remains authority; this hub is a best-effort UX side effect.
 */
@Component
public class AccountSessionEventHub {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountSessionEventHub.class);
    private static final String ACCOUNT_DISABLED_CODE = "ACCOUNT_DISABLED";
    private static final String ACCOUNT_DISABLED_EVENT = "account-disabled";
    private static final String HEARTBEAT_EVENT = "heartbeat";

    private final ConcurrentHashMap<ProfileKey, CopyOnWriteArraySet<SessionSubscription>> subscriptions =
            new ConcurrentHashMap<>();
    private final CurrentAccountStatusService accountStatusService;
    private final Clock clock;
    private final long emitterTimeoutMs;

    public AccountSessionEventHub(
            CurrentAccountStatusService accountStatusService,
            Clock clock,
            @Value("${app.auth.session-events.emitter-timeout-ms:1800000}") long emitterTimeoutMs
    ) {
        this.accountStatusService = accountStatusService;
        this.clock = clock;
        this.emitterTimeoutMs = emitterTimeoutMs;
    }

    public SseEmitter subscribe(SagaPrincipal principal, HttpSession session) {
        if (principal == null || principal.localProfileId() == null || principal.applicationRole() == null) {
            throw new IllegalArgumentException("Authenticated principal is required");
        }
        requireUsableSession(session);
        ProfileKey key = new ProfileKey(principal.applicationRole(), principal.localProfileId());
        SseEmitter emitter = new SseEmitter(emitterTimeoutMs);
        SessionSubscription subscription = new SessionSubscription(UUID.randomUUID(), key, emitter, session);
        emitter.onCompletion(() -> remove(subscription));
        emitter.onTimeout(() -> {
            remove(subscription);
            completeQuietly(emitter);
        });
        emitter.onError(error -> remove(subscription));
        subscriptions.computeIfAbsent(key, ignored -> new CopyOnWriteArraySet<>()).add(subscription);
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountDisabled(AccountDisabledEvent event) {
        if (event == null || event.applicationRole() == null || event.localProfileId() == null) {
            return;
        }
        try {
            revoke(event.applicationRole(), event.localProfileId());
        } catch (RuntimeException exception) {
            LOGGER.warn("Realtime account revocation side effect failed after commit");
        }
    }

    public void revoke(ApplicationRole role, UUID localProfileId) {
        if (role == null || localProfileId == null || role == ApplicationRole.ADMIN) {
            return;
        }
        CopyOnWriteArraySet<SessionSubscription> targets =
                subscriptions.remove(new ProfileKey(role, localProfileId));
        if (targets == null || targets.isEmpty()) {
            return;
        }
        String payload = accountDisabledPayload();
        for (SessionSubscription subscription : targets) {
            try {
                sendNamed(subscription.emitter(), ACCOUNT_DISABLED_EVENT, payload);
            } catch (Exception exception) {
                LOGGER.debug("Failed to push account-disabled event to one emitter");
            }
            completeQuietly(subscription.emitter());
            invalidateQuietly(subscription.session());
        }
    }

    public void revalidateConnectedProfiles() {
        Set<UUID> studentIds = new LinkedHashSet<>();
        Set<UUID> lecturerIds = new LinkedHashSet<>();
        for (ProfileKey key : subscriptions.keySet()) {
            if (key.role() == ApplicationRole.STUDENT) {
                studentIds.add(key.localProfileId());
            } else if (key.role() == ApplicationRole.LECTURER) {
                lecturerIds.add(key.localProfileId());
            }
        }
        revokeDisabled(ApplicationRole.STUDENT, studentIds);
        revokeDisabled(ApplicationRole.LECTURER, lecturerIds);
    }

    public void sendHeartbeats() {
        for (SessionSubscription subscription : snapshot()) {
            try {
                sendNamed(subscription.emitter(), HEARTBEAT_EVENT, "{}");
            } catch (Exception exception) {
                remove(subscription);
                completeQuietly(subscription.emitter());
            }
        }
    }

    public int activeSubscriptionCount() {
        return snapshot().size();
    }

    public int activeSubscriptionCount(ApplicationRole role, UUID localProfileId) {
        CopyOnWriteArraySet<SessionSubscription> current =
                subscriptions.get(new ProfileKey(role, localProfileId));
        return current == null ? 0 : current.size();
    }

    public void completeAllAndClear() {
        List<SessionSubscription> remaining = snapshot();
        subscriptions.clear();
        for (SessionSubscription subscription : remaining) {
            completeQuietly(subscription.emitter());
        }
    }

    private void revokeDisabled(ApplicationRole role, Collection<UUID> ids) {
        for (UUID disabledId : accountStatusService.findDisabledIds(role, ids)) {
            revoke(role, disabledId);
        }
    }

    private void remove(SessionSubscription subscription) {
        subscriptions.computeIfPresent(subscription.key(), (key, current) -> {
            current.remove(subscription);
            return current.isEmpty() ? null : current;
        });
    }

    private List<SessionSubscription> snapshot() {
        List<SessionSubscription> copy = new ArrayList<>();
        for (CopyOnWriteArraySet<SessionSubscription> current : subscriptions.values()) {
            copy.addAll(current);
        }
        return copy;
    }

    String accountDisabledPayload() {
        return "{\"code\":\"" + ACCOUNT_DISABLED_CODE
                + "\",\"occurredAt\":\"" + Instant.now(clock) + "\"}";
    }

    private static void sendNamed(SseEmitter emitter, String eventName, String data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    private static void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // Emitter already completed or timed out.
        }
    }

    private static void invalidateQuietly(HttpSession session) {
        if (session == null) {
            return;
        }
        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
            // Session was already invalidated.
        }
    }

    private static void requireUsableSession(HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session is required");
        }
        try {
            session.getCreationTime();
        } catch (IllegalStateException exception) {
            throw new IllegalArgumentException("Session is no longer usable");
        }
    }

    record ProfileKey(ApplicationRole role, UUID localProfileId) {
    }

    record SessionSubscription(
            UUID id,
            ProfileKey key,
            SseEmitter emitter,
            HttpSession session
    ) {
        @Override
        public boolean equals(Object other) {
            return other instanceof SessionSubscription subscription && id.equals(subscription.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
}
