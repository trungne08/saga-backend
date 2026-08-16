package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.saga.be.repository.CommitReviewIntentRepository;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class CommitReviewTransactionBoundaryTest {

    @Test
    void httpOrchestrationStaysOutsideTransaction() throws Exception {
        assertNotSupportedWritable("drainPendingAndPoll");
        assertNotSupportedWritable("startQueued", UUID.class);
        assertNotSupportedWritable("poll", UUID.class);
    }

    @Test
    void persistenceTransitionsStayIsolatedAndWritable() throws Exception {
        assertRequiresNewWritable(CommitReviewResultPersistenceService.class, "persistOnce");
        assertRequiresNewWritable(CommitReviewIntentService.class, "markStarted", UUID.class, UUID.class, String.class, String.class);
        assertRequiresNewWritable(CommitReviewIntentService.class, "restorePending", UUID.class, String.class);
        assertRequiresNewWritable(CommitReviewIntentService.class, "markPolled", UUID.class, com.saga.be.dto.response.CommitReviewJobResponses.Status.class);
        assertRequiresNewWritable(CommitReviewIntentService.class, "markFailed", UUID.class, String.class);
        Transactional claim = CommitReviewIntentService.class
                .getMethod("claimPendingForStart", UUID.class)
                .getAnnotation(Transactional.class);
        assertNotNull(claim);
        assertFalse(claim.readOnly());
    }

    @Test
    void historicalDigestPublishIsWritableBecauseItEmitsWarnings() throws Exception {
        Transactional annotation = CommitReviewHistoricalDiscoveryService.class
                .getMethod("publishBoundedDigests")
                .getAnnotation(Transactional.class);
        assertNotNull(annotation);
        assertFalse(annotation.readOnly());
        assertEquals(Propagation.REQUIRED, annotation.propagation());
    }

    @Test
    void historicalDiscoveryWritePageStaysWritable() throws Exception {
        Transactional annotation = CommitReviewHistoricalDiscoveryService.class
                .getMethod("discoverBoundedPage")
                .getAnnotation(Transactional.class);
        assertNotNull(annotation);
        assertFalse(annotation.readOnly());
    }

    private static void assertNotSupportedWritable(String methodName, Class<?>... parameterTypes) throws Exception {
        Transactional annotation = CommitReviewOrchestrator.class
                .getMethod(methodName, parameterTypes)
                .getAnnotation(Transactional.class);
        assertNotNull(annotation);
        assertEquals(Propagation.NOT_SUPPORTED, annotation.propagation());
        assertFalse(annotation.readOnly());
    }

    private static void assertRequiresNewWritable(Class<?> type, String methodName, Class<?>... parameterTypes)
            throws Exception {
        Method method = parameterTypes.length == 0
                ? findUnique(type, methodName)
                : type.getMethod(methodName, parameterTypes);
        Transactional annotation = method.getAnnotation(Transactional.class);
        assertNotNull(annotation);
        assertEquals(Propagation.REQUIRES_NEW, annotation.propagation());
        assertFalse(annotation.readOnly());
    }

    private static Method findUnique(Class<?> type, String methodName) {
        List<Method> matches = java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .toList();
        assertEquals(1, matches.size());
        return matches.get(0);
    }
}
