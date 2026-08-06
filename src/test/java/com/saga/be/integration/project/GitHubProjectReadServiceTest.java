package com.saga.be.integration.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saga.be.entity.GitHubInstallation;
import com.saga.be.entity.GitRepo;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.provider.GitHubBranchCommitInfo;
import com.saga.be.integration.provider.GitHubBranchInfo;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.ProjectDetailService;
import java.time.Instant;
import java.util.List;
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

    private static final class Fixture {
        final ProjectDetailService projects = mock(ProjectDetailService.class);
        final GitRepoRepository repos = mock(GitRepoRepository.class);
        final GitHubProviderClient provider = mock(GitHubProviderClient.class);
        final GitHubProjectReadService service = new GitHubProjectReadService(projects, repos, provider);
        final SagaPrincipal actor = mock(SagaPrincipal.class);
        final UUID projectId = UUID.randomUUID();
        Fixture() {
            GitHubInstallation installation = new GitHubInstallation(); installation.setInstallationId(7L);
            GitRepo repo = GitRepo.builder().repositoryId(99L).ownerLogin("owner").name("repo").fullName("owner/repo").installation(installation).build(); repo.setId(UUID.randomUUID());
            when(repos.findByProjectIdAndRepositoryId(projectId, 99L)).thenReturn(Optional.of(repo));
        }
    }
}
