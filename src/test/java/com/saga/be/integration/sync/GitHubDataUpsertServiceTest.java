package com.saga.be.integration.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saga.be.entity.CommitData;
import com.saga.be.entity.GitIssue;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.Student;
import com.saga.be.entity.enums.IssueState;
import com.saga.be.integration.identity.IdentityMappingService;
import com.saga.be.integration.provider.GitHubCommitSnapshot;
import com.saga.be.integration.provider.GitHubIssueSnapshot;
import com.saga.be.repository.CommentRepository;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.GitIssueRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.PrReviewRepository;
import com.saga.be.repository.PullRequestRepository;
import com.saga.be.service.CommitReviewIntentService;
import com.saga.be.service.TeamContributionRefreshService;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class GitHubDataUpsertServiceTest {

    private GitRepoRepository repoRepository;
    private GitIssueRepository issueRepository;
    private IdentityMappingService mappingService;
    private TeamContributionRefreshService teamContributionRefreshService;
    private CommitDataRepository commitRepository;
    private CommitReviewIntentService commitReviewIntentService;
    private GitHubDataUpsertService service;

    @BeforeEach
    void setUp() {
        repoRepository = mock(GitRepoRepository.class);
        issueRepository = mock(GitIssueRepository.class);
        mappingService = mock(IdentityMappingService.class);
        teamContributionRefreshService = mock(TeamContributionRefreshService.class);
        commitRepository = mock(CommitDataRepository.class);
        commitReviewIntentService = mock(CommitReviewIntentService.class);
        service = new GitHubDataUpsertService(
                repoRepository,
                issueRepository,
                commitRepository,
                mock(PullRequestRepository.class),
                mock(PrReviewRepository.class),
                mock(CommentRepository.class),
                mappingService,
                teamContributionRefreshService,
                commitReviewIntentService
        );
    }

    @Test
    void issueEndpointPullRequestShapeIsNeverStoredAsIssue() {
        GitHubIssueSnapshot pullRequestShape = snapshot(
                true,
                LocalDateTime.parse("2026-07-29T10:00:00")
        );

        GitHubDataUpsertService.IssueResult result = service.upsertIssue(
                UUID.randomUUID(),
                pullRequestShape
        );

        assertEquals(
                GitHubDataUpsertService.IssueResult.SKIPPED_AS_PULL_REQUEST,
                result
        );
        verifyNoInteractions(repoRepository, issueRepository, mappingService);
    }

    @Test
    void staleIssueEventCannotOverwriteNewerStoredState() {
        UUID repoId = UUID.randomUUID();
        GitRepo repository = new GitRepo();
        GitIssue existing = new GitIssue();
        existing.setTitle("new title");
        existing.setExternalUpdatedAt(
                LocalDateTime.parse("2026-07-29T11:00:00")
        );
        when(repoRepository.findById(repoId))
                .thenReturn(Optional.of(repository));
        when(issueRepository.findByRepoIdAndGithubIssueId(repoId, 101L))
                .thenReturn(Optional.of(existing));

        GitHubDataUpsertService.IssueResult result = service.upsertIssue(
                repoId,
                snapshot(false, LocalDateTime.parse("2026-07-29T10:00:00"))
        );

        assertEquals(
                GitHubDataUpsertService.IssueResult.IGNORED_STALE,
                result
        );
        assertEquals("new title", existing.getTitle());
        verify(issueRepository, never()).saveAndFlush(any());
    }

    @Test
    void historicalAttributionIsPreservedForSameStableExternalId() {
        UUID repoId = UUID.randomUUID();
        GitRepo repository = new GitRepo();
        Student historicallyMapped = new Student();
        GitIssue existing = new GitIssue();
        existing.setAuthor(historicallyMapped);
        existing.setAuthorExternalId("55");
        when(repoRepository.findById(repoId))
                .thenReturn(Optional.of(repository));
        when(issueRepository.findByRepoIdAndGithubIssueId(repoId, 101L))
                .thenReturn(Optional.of(existing));
        when(issueRepository.saveAndFlush(any(GitIssue.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GitHubDataUpsertService.IssueResult result = service.upsertIssue(
                repoId,
                snapshot(false, LocalDateTime.parse("2026-07-29T10:00:00"))
        );

        assertEquals(GitHubDataUpsertService.IssueResult.UPSERTED, result);
        ArgumentCaptor<GitIssue> captor = ArgumentCaptor.forClass(GitIssue.class);
        verify(issueRepository).saveAndFlush(captor.capture());
        assertSame(historicallyMapped, captor.getValue().getAuthor());
        verifyNoInteractions(mappingService);
    }

    @Test
    void newIssueUsesStableNumericUserIdForAttribution() {
        UUID repoId = UUID.randomUUID();
        Student mapped = new Student();
        when(repoRepository.findById(repoId))
                .thenReturn(Optional.of(new GitRepo()));
        when(issueRepository.findByRepoIdAndGithubIssueId(repoId, 101L))
                .thenReturn(Optional.empty());
        when(mappingService.findActiveStudent(
                com.saga.be.entity.enums.IntegrationProvider.GITHUB,
                "55"
        )).thenReturn(Optional.of(mapped));
        when(issueRepository.saveAndFlush(any(GitIssue.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upsertIssue(
                repoId,
                snapshot(false, LocalDateTime.parse("2026-07-29T10:00:00"))
        );

        ArgumentCaptor<GitIssue> captor = ArgumentCaptor.forClass(GitIssue.class);
        verify(issueRepository).saveAndFlush(captor.capture());
        assertSame(mapped, captor.getValue().getAuthor());
        assertEquals("55", captor.getValue().getAuthorExternalId());
        assertEquals(IssueState.OPEN, captor.getValue().getState());
    }

    @Test
    void newCanonicalCommitEnqueuesExactlyOneReviewIntent() {
        UUID repoId = UUID.randomUUID();
        GitRepo repository = new GitRepo();
        ReflectionTestUtils.setField(repository, "id", repoId);
        repository.setReviewCutoverAt(LocalDateTime.parse("2026-08-01T00:00:00"));
        when(repoRepository.findById(repoId)).thenReturn(Optional.of(repository));
        when(commitRepository.findByRepoIdAndShaHash(repoId, "deadbeef".repeat(5))).thenReturn(Optional.empty());
        when(commitRepository.saveAndFlush(any(CommitData.class))).thenAnswer(invocation -> {
            CommitData commit = invocation.getArgument(0);
            if (commit.getId() == null) {
                ReflectionTestUtils.setField(commit, "id", UUID.randomUUID());
            }
            return commit;
        });

        boolean first = service.upsertCommit(repoId, commitSnapshot("deadbeef".repeat(5),
                LocalDateTime.parse("2026-08-02T00:00:00")));
        CommitData persisted = new CommitData();
        ReflectionTestUtils.setField(persisted, "id", UUID.randomUUID());
        persisted.setShaHash("deadbeef".repeat(5));
        when(commitRepository.findByRepoIdAndShaHash(repoId, "deadbeef".repeat(5)))
                .thenReturn(Optional.of(persisted));
        boolean second = service.upsertCommit(repoId, commitSnapshot("deadbeef".repeat(5),
                LocalDateTime.parse("2026-08-02T00:00:00")));

        assertTrue(first);
        assertTrue(second);
        verify(commitReviewIntentService).enqueueNewCanonicalCommit(any(GitRepo.class), any(CommitData.class));
    }

    private GitHubCommitSnapshot commitSnapshot(String sha, LocalDateTime committedAt) {
        return new GitHubCommitSnapshot(sha, 55L, "SAGA-1 message only is not traceability", committedAt,
                1, 0, 1, committedAt);
    }

    private GitHubIssueSnapshot snapshot(
            boolean pullRequest,
            LocalDateTime updatedAt
    ) {
        return new GitHubIssueSnapshot(
                101L,
                "node-101",
                12,
                "issue title",
                "open",
                55L,
                null,
                pullRequest,
                updatedAt,
                null
        );
    }
}
