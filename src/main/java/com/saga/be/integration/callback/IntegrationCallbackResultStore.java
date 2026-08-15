package com.saga.be.integration.callback;

import com.saga.be.config.IntegrationCallbackProperties;
import com.saga.be.dto.response.IntegrationCallbackFlow;
import com.saga.be.dto.response.IntegrationCallbackResultResponse;
import com.saga.be.exception.IntegrationException;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/** Stores only safe callback summaries in the current HTTP session. */
@Service
public class IntegrationCallbackResultStore {

    static final String SESSION_ATTRIBUTE =
            IntegrationCallbackResultStore.class.getName() + ".pending";
    private static final int MAX_PENDING_RESULTS = 10;
    private final IntegrationCallbackProperties properties;
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public IntegrationCallbackResultStore(IntegrationCallbackProperties properties) {
        this(properties, new SecureRandom(), Clock.systemUTC());
    }

    IntegrationCallbackResultStore(
            IntegrationCallbackProperties properties,
            SecureRandom secureRandom,
            Clock clock
    ) {
        this.properties = properties;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    public String store(
            HttpSession session,
            SagaPrincipal principal,
            IntegrationCallbackResultResponse result
    ) {
        synchronized (session) {
            Map<String, StoredResult> pending = pending(session);
            Instant now = clock.instant();
            removeExpired(pending, now);
            while (pending.size() >= MAX_PENDING_RESULTS) {
                pending.remove(pending.keySet().iterator().next());
            }
            String resultId = nextId(pending);
            pending.put(resultId, new StoredResult(
                    principal.cognitoSub(),
                    principal.localProfileId(),
                    result,
                    now,
                    now.plus(properties.callbackResultTtl())
            ));
            return resultId;
        }
    }

    public IntegrationCallbackResultResponse consume(
            HttpSession session,
            SagaPrincipal principal,
            String resultId,
            Consumer<IntegrationCallbackResultResponse> projectAuthorizer
    ) {
        synchronized (session) {
            Map<String, StoredResult> pending = pending(session);
            Instant now = clock.instant();
            removeExpired(pending, now);
            StoredResult stored = pending.get(resultId);
            if (stored == null || !samePrincipal(stored, principal)) {
                throw unavailable();
            }
            if (stored.result().flow() == IntegrationCallbackFlow.PERSONAL) {
                requireStudent(principal);
            } else {
                projectAuthorizer.accept(stored.result());
            }
            pending.remove(resultId);
            return stored.result();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, StoredResult> pending(HttpSession session) {
        Object attribute = session.getAttribute(SESSION_ATTRIBUTE);
        if (attribute instanceof Map<?, ?> map) {
            return (Map<String, StoredResult>) map;
        }
        Map<String, StoredResult> pending = new LinkedHashMap<>();
        session.setAttribute(SESSION_ATTRIBUTE, pending);
        return pending;
    }

    private void removeExpired(Map<String, StoredResult> pending, Instant now) {
        Iterator<Map.Entry<String, StoredResult>> iterator =
                pending.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().getValue().expiresAt().isAfter(now)) {
                iterator.remove();
            }
        }
    }

    private String nextId(Map<String, StoredResult> pending) {
        byte[] bytes = new byte[32];
        String value;
        do {
            secureRandom.nextBytes(bytes);
            value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (pending.containsKey(value));
        return value;
    }

    private boolean samePrincipal(StoredResult stored, SagaPrincipal principal) {
        return principal != null
                && Objects.equals(stored.cognitoSub(), principal.cognitoSub())
                && Objects.equals(
                        stored.localProfileId(),
                        principal.localProfileId()
                );
    }

    private void requireStudent(SagaPrincipal principal) {
        if (principal.applicationRole() != ApplicationRole.STUDENT
                || principal.localProfileId() == null) {
            throw IntegrationException.forbidden(
                    "Only a Student may consume a personal integration result"
            );
        }
    }

    private IntegrationException unavailable() {
        return IntegrationException.conflict(
                "INTEGRATION_CALLBACK_RESULT_UNAVAILABLE",
                "The callback result is missing, expired, or already consumed"
        );
    }

    private record StoredResult(
            String cognitoSub,
            UUID localProfileId,
            IntegrationCallbackResultResponse result,
            Instant createdAt,
            Instant expiresAt
    ) {
    }
}
