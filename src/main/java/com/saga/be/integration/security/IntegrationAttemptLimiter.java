package com.saga.be.integration.security;

import com.saga.be.exception.IntegrationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class IntegrationAttemptLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ConcurrentHashMap<String, Deque<Instant>> attempts =
            new ConcurrentHashMap<>();
    private final Clock clock;

    public IntegrationAttemptLimiter() {
        this(Clock.systemUTC());
    }

    IntegrationAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public void requireAllowed(String key) {
        Instant now = clock.instant();
        Deque<Instant> timestamps = attempts.computeIfAbsent(
                key,
                ignored -> new ArrayDeque<>()
        );
        synchronized (timestamps) {
            Instant cutoff = now.minus(WINDOW);
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= MAX_ATTEMPTS) {
                throw new IntegrationException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "INTEGRATION_RATE_LIMITED",
                        "Too many integration attempts; try again later"
                );
            }
            timestamps.addLast(now);
        }
    }
}
