package com.saga.be.integration.callback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.saga.be.config.IntegrationCallbackProperties;
import com.saga.be.dto.response.IdentityConnectionResponse;
import com.saga.be.dto.response.IntegrationCallbackResultResponse;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.IdentityMappingStatus;
import com.saga.be.entity.enums.IntegrationProvider;
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

class IntegrationCallbackResultStoreTest {

    @Test
    void consumesAResultOnlyOnceForItsBoundSessionAndPrincipal() {
        IntegrationCallbackResultStore store = store();
        MockHttpSession session = new MockHttpSession();
        SagaPrincipal owner = student("owner");
        IntegrationCallbackResultResponse result = personalResult();
        String resultId = store.store(session, owner, result);

        assertEquals(result, store.consume(session, owner, resultId, ignored -> { }));
        assertUnavailable(() -> store.consume(
                session, owner, resultId, ignored -> { }
        ));
    }

    @Test
    void doesNotRevealAResultToAnotherSessionOrPrincipal() {
        IntegrationCallbackResultStore store = store();
        SagaPrincipal owner = student("owner");
        String resultId = store.store(new MockHttpSession(), owner, personalResult());

        assertUnavailable(() -> store.consume(
                new MockHttpSession(), student("other"), resultId, ignored -> { }
        ));
    }

    @Test
    void expiresResultsBeforeTheyCanBeConsumed() {
        TestClock clock = new TestClock(Instant.parse("2026-01-01T00:00:00Z"));
        IntegrationCallbackResultStore store = store(clock);
        MockHttpSession session = new MockHttpSession();
        SagaPrincipal owner = student("owner");
        String resultId = store.store(session, owner, personalResult());
        clock.current = clock.current.plus(Duration.ofMinutes(5));

        assertUnavailable(() -> store.consume(
                session, owner, resultId, ignored -> { }
        ));
    }

    private IntegrationCallbackResultStore store() {
        return store(new TestClock(Instant.parse("2026-01-01T00:00:00Z")));
    }

    private IntegrationCallbackResultStore store(Clock clock) {
        return new IntegrationCallbackResultStore(
                new IntegrationCallbackProperties(
                        "https://frontend.example/integrations/callback",
                        Duration.ofMinutes(5)
                ),
                new SecureRandom(),
                clock
        );
    }

    private IntegrationCallbackResultResponse personalResult() {
        return IntegrationCallbackResultResponse.personalSuccess(
                new IdentityConnectionResponse(
                        IntegrationProvider.JIRA,
                        IdentityMappingStatus.ACTIVE,
                        "Student",
                        "student@example.com",
                        null,
                        null
                )
        );
    }

    private SagaPrincipal student(String sub) {
        return new SagaPrincipal(
                sub,
                sub + "@example.com",
                sub,
                ApplicationRole.STUDENT,
                UUID.nameUUIDFromBytes(sub.getBytes()),
                AccountStatus.ACTIVE
        );
    }

    private void assertUnavailable(org.junit.jupiter.api.function.Executable action) {
        IntegrationException exception = assertThrows(IntegrationException.class, action);
        assertEquals("INTEGRATION_CALLBACK_RESULT_UNAVAILABLE", exception.getCode());
    }

    private static final class TestClock extends Clock {
        private Instant current;

        private TestClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
