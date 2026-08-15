package com.saga.be.integration.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Project;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.ProjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
class JiraBoardLinkConcurrencyIntegrationTest {

    @Autowired
    private JiraBoardLinkPersistenceService service;

    @Autowired
    private JiraBoardRepository boards;

    @Autowired
    private ProjectRepository projects;

    @MockitoBean
    private JiraCredentialService credentials;

    @AfterEach
    void removeOnlyFixturesCreatedByThisTest() {
        boards.deleteAll();
        projects.deleteAll();
    }

    @Test
    void concurrentRelinksForSameProjectAndProviderCoalesceToOneBoard() throws Exception {
        Project project = projects.saveAndFlush(Project.builder().name("Concurrent one").build());
        configureCredentials();
        JiraBoardLinkCommand command = command(project, "cloud-concurrent", "10034");

        List<Outcome> outcomes = runTogether(
                () -> upsertWithRaceFallback(command),
                () -> upsertWithRaceFallback(command)
        );

        assertTrue(outcomes.stream().allMatch(Outcome::succeeded));
        assertEquals(1, boards.findAll().size());
        assertEquals(outcomes.get(0).boardId(), outcomes.get(1).boardId());
    }

    @Test
    void concurrentRelinksFromDifferentProjectsForSameProviderLeaveOneOwner() throws Exception {
        Project first = projects.saveAndFlush(Project.builder().name("Concurrent first").build());
        Project second = projects.saveAndFlush(Project.builder().name("Concurrent second").build());
        configureCredentials();

        List<Outcome> outcomes = runTogether(
                () -> upsertWithRaceFallback(command(first, "cloud-concurrent", "10034")),
                () -> upsertWithRaceFallback(command(second, "cloud-concurrent", "10034"))
        );

        assertEquals(1, outcomes.stream().filter(Outcome::succeeded).count());
        assertEquals(1, outcomes.stream()
                .filter(outcome -> "JIRA_PROJECT_ALREADY_LINKED".equals(outcome.errorCode()))
                .count());
        assertEquals(1, boards.findAll().size());
    }

    private void configureCredentials() {
        when(credentials.encryptAccess(any(), any())).thenReturn("fresh-access-ciphertext");
        when(credentials.encryptRefresh(any(), any())).thenReturn("fresh-refresh-ciphertext");
    }

    private Outcome upsertWithRaceFallback(JiraBoardLinkCommand command) {
        try {
            return Outcome.success(service.upsert(command).getId());
        } catch (DataIntegrityViolationException race) {
            try {
                return Outcome.success(service.upsert(command).getId());
            } catch (IntegrationException conflict) {
                return Outcome.failure(conflict.getCode());
            }
        } catch (IntegrationException conflict) {
            return Outcome.failure(conflict.getCode());
        }
    }

    private List<Outcome> runTogether(Callable<Outcome> first, Callable<Outcome> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> firstFuture = executor.submit(() -> awaitAndCall(ready, start, first));
            Future<Outcome> secondFuture = executor.submit(() -> awaitAndCall(ready, start, second));
            ready.await();
            start.countDown();
            return List.of(firstFuture.get(), secondFuture.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private Outcome awaitAndCall(
            CountDownLatch ready,
            CountDownLatch start,
            Callable<Outcome> operation
    ) throws Exception {
        ready.countDown();
        start.await();
        return operation.call();
    }

    private JiraBoardLinkCommand command(Project project, String cloudId, String jiraProjectId) {
        return new JiraBoardLinkCommand(
                project,
                "Concurrent Jira Project",
                cloudId,
                "https://site.example",
                jiraProjectId,
                "SAGA",
                "99",
                "fresh-access-token",
                "fresh-refresh-token",
                Instant.now().plusSeconds(3600),
                Set.of("read:jira-work"),
                "concurrent-actor",
                null
        );
    }

    private record Outcome(UUID boardId, String errorCode) {
        static Outcome success(UUID boardId) {
            return new Outcome(boardId, null);
        }

        static Outcome failure(String errorCode) {
            return new Outcome(null, errorCode);
        }

        boolean succeeded() {
            return boardId != null;
        }
    }
}
