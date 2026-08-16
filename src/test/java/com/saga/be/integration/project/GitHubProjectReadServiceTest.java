package com.saga.be.integration.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.dto.response.CommitReviewSummary;
import com.saga.be.entity.GitHubInstallation;
import com.saga.be.entity.GitRepo;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.provider.GitHubBranchCommitInfo;
import com.saga.be.integration.provider.GitHubBranchInfo;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.CommitReviewSummaryResolver;
import com.saga.be.service.ProjectDetailService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GitHubProjectReadServiceTest {
    @Test
    void pagesBranchesAndScopesRepositoryToProject() {
        Fixture f = new Fixture();
        when(f.provider.branches(7L, "owner", "repo")).thenReturn(List.of(
                new GitHubBranchInfo("main", "a", true), new GitHubBranchInfo("feature/x", "b", false)));
        var response = f.service.branches(f.actor, f.projectId, 99L, 1, 1);
        assertEquals("feature/x", response.branches().content().get(0).name());
        assertFalse(response.branches().hasNext());
    }

    @Test
    void preservesSlashInBranchQueryValue() {
        Fixture f = new Fixture();
        when(f.provider.branchCommits(7L, "owner", "repo", "feature/project-setup")).thenReturn(List.of(
                new GitHubBranchCommitInfo("abc", "message", null, null, Instant.EPOCH, Instant.EPOCH, null)));
        var response = f.service.commits(f.actor, f.projectId, 99L, " feature/project-setup ", 0, 20);
        assertEquals("feature/project-setup", response.branch());
        assertEquals("abc", response.commits().content().get(0).sha());
    }

    @Test
    void rejectsBlankBranchBeforeProviderCall() {
        Fixture f = new Fixture();
        assertEquals("GITHUB_BRANCH_INVALID", assertThrows(IntegrationException.class,
                () -> f.service.commits(f.actor, f.projectId, 99L, " ", 0, 20)).getCode());
    }

    @Test
    void reviewIsNullWhenResolverHasNoSummaryForTheSha() {
        Fixture f = new Fixture();
        when(f.provider.branchCommits(7L, "owner", "repo", "main")).thenReturn(List.of(
                new GitHubBranchCommitInfo("abc", "message", null, null, Instant.EPOCH, Instant.EPOCH, null)));
        when(f.reviewSummaries.resolve(any(), any())).thenReturn(Map.of());
        var response = f.service.commits(f.actor, f.projectId, 99L, "main", 0, 20);
        assertNull(response.commits().content().get(0).review());
    }

    @Test
    void mapsResolverSummaryOntoMatchingCommit() {
        Fixture f = new Fixture();
        when(f.provider.branchCommits(7L, "owner", "repo", "main")).thenReturn(List.of(
                new GitHubBranchCommitInfo("abc", "message", null, null, Instant.EPOCH, Instant.EPOCH, null)));
        CommitReviewSummary summary = new CommitReviewSummary(
                com.saga.be.entity.enums.CommitReviewIntentStatus.COMPLETED, "TASK_LINKED",
                Instant.EPOCH, Instant.EPOCH,
                new CommitReviewSummary.Result("VERIFIED", "GOOD", "GOOD", "ALIGNED", true, "PASS", "PASS"));
        when(f.reviewSummaries.resolve(f.repo.getId(), List.of("abc"))).thenReturn(Map.of("abc", summary));
        var response = f.service.commits(f.actor, f.projectId, 99L, "main", 0, 20);
        assertEquals(summary, response.commits().content().get(0).review());
    }

    @Test
    void resolvesReviewSummariesOncePerPageNotPerCommit() {
        Fixture f = new Fixture();
        when(f.provider.branchCommits(7L, "owner", "repo", "main")).thenReturn(List.of(
                new GitHubBranchCommitInfo("sha-1", "m1", null, null, Instant.EPOCH, Instant.EPOCH, null),
                new GitHubBranchCommitInfo("sha-2", "m2", null, null, Instant.EPOCH, Instant.EPOCH, null),
                new GitHubBranchCommitInfo("sha-3", "m3", null, null, Instant.EPOCH, Instant.EPOCH, null)));
        when(f.reviewSummaries.resolve(any(), any())).thenReturn(Map.of());
        f.service.commits(f.actor, f.projectId, 99L, "main", 0, 20);
        verify(f.reviewSummaries, times(1)).resolve(any(), any());
    }

    private static final class Fixture {
        final ProjectDetailService projects = mock(ProjectDetailService.class);
        final GitRepoRepository repos = mock(GitRepoRepository.class);
        final GitHubProviderClient provider = mock(GitHubProviderClient.class);
        final CommitReviewSummaryResolver reviewSummaries = mock(CommitReviewSummaryResolver.class);
        final GitHubProjectReadService service = new GitHubProjectReadService(projects, repos, provider, reviewSummaries);
        final SagaPrincipal actor = mock(SagaPrincipal.class);
        final UUID projectId = UUID.randomUUID();
        final GitRepo repo;
        Fixture() {
            GitHubInstallation installation = new GitHubInstallation(); installation.setInstallationId(7L);
            repo = GitRepo.builder().repositoryId(99L).ownerLogin("owner").name("repo").fullName("owner/repo").installation(installation).build(); repo.setId(UUID.randomUUID());
            when(repos.findByProjectIdAndRepositoryId(projectId, 99L)).thenReturn(Optional.of(repo));
        }
    }
}
