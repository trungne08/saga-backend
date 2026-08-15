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

import com.saga.be.entity.GitIssue;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.Student;
import com.saga.be.entity.enums.IssueState;
import com.saga.be.integration.identity.IdentityMappingService;
import com.saga.be.integration.provider.GitHubIssueSnapshot;
import com.saga.be.repository.CommentRepository;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.GitIssueRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.PrReviewRepository;
import com.saga.be.repository.PullRequestRepository;
import com.saga.be.service.TeamContributionRefreshService;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GitHubDataUpsertServiceTest {

    private GitRepoRepository repoRepository;
    private GitIssueRepository issueRepository;
    private IdentityMappingService mappingService;
    private TeamContributionRefreshService teamContributionRefreshService;
    private GitHubDataUpsertService service;

    @BeforeEach
    void setUp() {
        repoRepository = mock(GitRepoRepository.class);
        issueRepository = mock(GitIssueRepository.class);
        mappingService = mock(IdentityMappingService.class);
        teamContributionRefreshService = mock(TeamContributionRefreshService.class);
        service = new GitHubDataUpsertService(
                repoRepository,
                issueRepository,
                mock(CommitDataRepository.class),
                mock(PullRequestRepository.class),
                mock(PrReviewRepository.class),
                mock(CommentRepository.class),
                mappingService,
                teamContributionRefreshService
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
