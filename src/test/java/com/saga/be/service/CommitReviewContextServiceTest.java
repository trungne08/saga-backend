package com.saga.be.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.dto.response.InternalCommitReviewContextResponse;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.provider.GitHubChangedFileSnapshot;
import com.saga.be.integration.provider.GitHubCommitDetailSnapshot;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.service.CommitReviewContextReader.CommitSnapshot;
import com.saga.be.service.CommitReviewContextReader.IssueLinkSnapshot;
import com.saga.be.service.CommitReviewContextReader.RepositorySnapshot;
import com.saga.be.service.CommitReviewContextReader.SourceSnapshot;
import com.saga.be.service.CommitReviewContextReader.TaskLinkSnapshot;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;

class CommitReviewContextServiceTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID REPOSITORY_LOCAL_ID = UUID.randomUUID();
    private static final UUID COMMIT_LOCAL_ID = UUID.randomUUID();
    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";
    private static final String SECRET = "synthetic-service-secret-12345678901234567890";

    private CommitReviewContextReader reader;
    private GitHubProviderClient github;
    private CommitReviewContextSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        reader = mock(CommitReviewContextReader.class);
        github = mock(GitHubProviderClient.class);
        sanitizer = new CommitReviewContextSanitizer(
                new MockEnvironment().withProperty("SAGA_AI_SERVICE_TOKEN", SECRET)
        );
    }

    @Test
    void successUsesExactProviderIdentityPatchAndExplicitNormalizedLinks() {
        SourceSnapshot source = sourceWithExplicitLinks();
        GitHubCommitDetailSnapshot detail = detail(List.of(file("src/Main.java", "patch")));
        stub(source, detail);

        InternalCommitReviewContextResponse result = service().context(PROJECT_ID, 42L, SHA);

        assertEquals("saga-commit-review-context-v1", result.contextSchemaVersion());
        assertEquals("SAGA_BACKEND", result.contextProvider());
        assertEquals(PROJECT_ID, result.project().projectId());
        assertEquals(REPOSITORY_LOCAL_ID, result.repository().localRepositoryId());
        assertEquals(42L, result.repository().repositoryId());
        assertEquals("GITHUB", result.repository().provider());
        assertEquals(SHA, result.commit().sha());
        assertEquals("patch", result.commit().changedFiles().get(0).patch());
        assertEquals("EXPLICIT_LOCAL_RELATION_ONLY", result.traceability().authority());
        assertEquals("EXPLICIT_LINKS_PRESENT", result.traceability().relationStatus());
        assertThat(result.traceability().evidenceRefs())
                .allMatch(value -> value.startsWith("git-issue-commit-link:")
                        || value.startsWith("task-git-issue-link:"));
        assertEquals("Task description is the requirement evidence.", result.traceability()
                .linkedIssues().get(0).linkedTasks().get(0).description());
        assertThat(result.toString()).doesNotContain("installationId", "ownerLogin");
    }

    @Test
    void absentNormalizedLinksRemainNotProvenDespiteMessageAndJiraKeyCoincidence() {
        SourceSnapshot source = source(List.of(), false, 1);
        GitHubCommitDetailSnapshot detail = new GitHubCommitDetailSnapshot(
                SHA,
                "Fix #42 for SAGA-42 and a matching task title",
                LocalDateTime.parse("2026-08-14T00:00:00"),
                1,
                0,
                List.of(file("src/Main.java", "patch"))
        );
        stub(source, detail);

        InternalCommitReviewContextResponse result = service().context(PROJECT_ID, 42L, SHA);

        assertEquals("NOT_PROVEN", result.traceability().relationStatus());
        assertThat(result.traceability().linkedIssues()).isEmpty();
        assertThat(result.traceability().evidenceRefs()).isEmpty();
    }

    @Test
    void changedFilePatchAndTotalContextAreBoundedWithExplicitMetadata() {
        List<GitHubChangedFileSnapshot> files = List.of(
                file("src/One.java", "abcdefghij"),
                file("src/Two.java", "klmnopqrst"),
                file("src/Three.java", "uvwxyz")
        );
        SourceSnapshot source = source(List.of(), false, 3);
        stub(source, detail(files));
        CommitReviewContextLimits limits = new CommitReviewContextLimits(2, 5, 80);

        InternalCommitReviewContextResponse result = service(limits)
                .context(PROJECT_ID, 42L, SHA);

        assertThat(result.commit().changedFiles()).hasSizeLessThanOrEqualTo(2);
        assertThat(result.commit().changedFiles())
                .allMatch(file -> file.patch() == null || file.patch().length() <= 5);
        assertThat(result.contextBounds().truncationReasons())
                .contains("MAX_CHANGED_FILES_EXCEEDED");
        assertThat(result.contextBounds().truncated()).isTrue();
        assertEquals(3, result.contextBounds().totalChangedFiles());
        assertEquals(result.commit().changedFiles().size(),
                result.contextBounds().includedChangedFiles());
    }

    @Test
    void recognizedAndExactConfiguredSecretsAreRedactedBeforeResponse() {
        TaskLinkSnapshot task = new TaskLinkSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "SAGA-42", "Task", "Never expose " + SECRET,
                "TASK", "TODO", "HIGH", 3
        );
        IssueLinkSnapshot issue = new IssueLinkSnapshot(
                UUID.randomUUID(), task.issueId(), 42, "Issue", "OPEN",
                "REFERENCE", List.of(task)
        );
        SourceSnapshot source = source(List.of(issue), false, 1);
        GitHubCommitDetailSnapshot detail = new GitHubCommitDetailSnapshot(
                SHA,
                "Do not expose " + SECRET,
                LocalDateTime.parse("2026-08-14T00:00:00"),
                1,
                0,
                List.of(file("src/Main.java", "Authorization: Bearer abcdefghijklmnopqrstuvwxyz"))
        );
        stub(source, detail);

        InternalCommitReviewContextResponse result = service().context(PROJECT_ID, 42L, SHA);
        String serialized = result.toString();

        assertThat(serialized).doesNotContain(SECRET, "abcdefghijklmnopqrstuvwxyz");
        assertThat(serialized).contains("[REDACTED_SECRET]");
        assertThat(result.contextBounds().truncationReasons())
                .contains("SECRET_REDACTION_APPLIED");
    }

    @Test
    void malformedOrMismatchedCommitIdentityFailsBeforeReturningContext() {
        IntegrationException invalid = assertThrows(
                IntegrationException.class,
                () -> service().context(PROJECT_ID, 42L, "not-a-sha")
        );
        assertEquals("GITHUB_COMMIT_SHA_INVALID", invalid.getCode());
        verify(reader, never()).load(PROJECT_ID, 42L, "not-a-sha");

        SourceSnapshot source = source(List.of(), false, 1);
        stub(source, new GitHubCommitDetailSnapshot(
                "ffffffffffffffffffffffffffffffffffffffff",
                "Wrong commit",
                null,
                0,
                0,
                List.of()
        ));
        IntegrationException mismatch = assertThrows(
                IntegrationException.class,
                () -> service().context(PROJECT_ID, 42L, SHA)
        );
        assertEquals("GITHUB_RESPONSE_INVALID", mismatch.getCode());
    }

    @Test
    void localAndProviderSafeFailureTaxonomyPropagatesWithoutRawPayload() {
        IntegrationException local = new IntegrationException(
                HttpStatus.NOT_FOUND,
                "GITHUB_COMMIT_NOT_FOUND",
                "The GitHub commit does not exist in this linked repository"
        );
        when(reader.load(PROJECT_ID, 42L, SHA)).thenThrow(local);
        assertEquals("GITHUB_COMMIT_NOT_FOUND", assertThrows(
                IntegrationException.class,
                () -> service().context(PROJECT_ID, 42L, SHA)
        ).getCode());
        verify(github, never()).commitDetail(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );

        SourceSnapshot source = source(List.of(), false, 1);
        doReturn(source).when(reader).load(PROJECT_ID, 42L, SHA);
        for (String code : List.of(
                "GITHUB_RATE_LIMITED",
                "GITHUB_PROVIDER_UNAVAILABLE",
                "GITHUB_RESOURCE_NOT_FOUND",
                "GITHUB_ACCESS_REVOKED"
        )) {
            doThrow(new IntegrationException(
                            code.equals("GITHUB_RATE_LIMITED")
                                    ? HttpStatus.TOO_MANY_REQUESTS
                                    : HttpStatus.BAD_GATEWAY,
                            code,
                            "Safe provider failure"
                    )).when(github).commitDetail(9001L, "saga", "backend", SHA);
            IntegrationException failure = assertThrows(
                    IntegrationException.class,
                    () -> service().context(PROJECT_ID, 42L, SHA)
            );
            assertEquals(code, failure.getCode());
            assertThat(failure.getMessage()).doesNotContain("token", "response body");
        }
    }

    private CommitReviewContextService service() {
        return service(CommitReviewContextLimits.defaults());
    }

    private CommitReviewContextService service(CommitReviewContextLimits limits) {
        return new CommitReviewContextService(reader, github, sanitizer, limits);
    }

    private void stub(SourceSnapshot source, GitHubCommitDetailSnapshot detail) {
        when(reader.load(PROJECT_ID, 42L, SHA)).thenReturn(source);
        when(github.commitDetail(9001L, "saga", "backend", SHA)).thenReturn(detail);
    }

    private SourceSnapshot sourceWithExplicitLinks() {
        UUID issueId = UUID.randomUUID();
        TaskLinkSnapshot task = new TaskLinkSnapshot(
                UUID.randomUUID(), issueId, UUID.randomUUID(), "SAGA-42",
                "Task title", "Task description is the requirement evidence.",
                "TASK", "TODO", "HIGH", 3
        );
        IssueLinkSnapshot issue = new IssueLinkSnapshot(
                UUID.randomUUID(), issueId, 42, "Issue title", "OPEN",
                "REFERENCE", List.of(task)
        );
        return source(List.of(issue), false, 1);
    }

    private SourceSnapshot source(
            List<IssueLinkSnapshot> linkedIssues,
            boolean traceabilityTruncated,
            int filesChanged
    ) {
        return new SourceSnapshot(
                PROJECT_ID,
                new RepositorySnapshot(
                        REPOSITORY_LOCAL_ID,
                        42L,
                        "GITHUB",
                        "saga",
                        "backend",
                        "saga/backend",
                        9001L
                ),
                new CommitSnapshot(
                        COMMIT_LOCAL_ID,
                        SHA,
                        "Local metadata",
                        LocalDateTime.parse("2026-08-14T00:00:00"),
                        1,
                        0,
                        filesChanged
                ),
                linkedIssues,
                traceabilityTruncated
        );
    }

    private GitHubCommitDetailSnapshot detail(List<GitHubChangedFileSnapshot> files) {
        return new GitHubCommitDetailSnapshot(
                SHA,
                "Provider commit detail",
                LocalDateTime.parse("2026-08-14T00:00:00"),
                1,
                0,
                files
        );
    }

    private GitHubChangedFileSnapshot file(String path, String patch) {
        return new GitHubChangedFileSnapshot(path, "modified", 1, 0, patch);
    }
}
