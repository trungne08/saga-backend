package com.saga.be.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * One bounded sweep for connected SSE profiles. Not one thread per connection.
 * Interval is the cross-instance detection bound while the event bus is process-local.
 */
@Component
@ConditionalOnProperty(
        name = "app.auth.session-events.revalidation-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AccountSessionRevalidationScheduler {

    private final AccountSessionEventHub hub;

    public AccountSessionRevalidationScheduler(AccountSessionEventHub hub) {
        this.hub = hub;
    }

    @Scheduled(fixedDelayString = "${app.auth.session-events.revalidation-interval-ms:5000}")
    public void revalidateAndHeartbeat() {
        hub.revalidateConnectedProfiles();
        hub.sendHeartbeats();
    }
}
