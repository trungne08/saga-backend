package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.CommitData;
import com.saga.be.entity.CommitReviewIntent;
import com.saga.be.entity.CommitReviewResult;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.Project;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.CommitReviewIntentRepository;
import com.saga.be.repository.CommitReviewResultRepository;
import com.saga.be.repository.GitRepoRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class ExtraMasterHistoricalDiscoveryTest {

    @Test
    void boundedPageDoesNotRescanExistingIntentAndDoesNotCallGithub() {
        CommitDataRepository commits = mock(CommitDataRepository.class);
        CommitReviewIntentService intents = mock(CommitReviewIntentService.class);
        CommitReviewIntentRepository intentRepository = mock(CommitReviewIntentRepository.class);
        GitRepo repo = GitRepo.builder().name("backend").fullName("saga/backend").build();
        repo.setId(UUID.randomUUID());
        CommitData fresh = CommitData.builder().repo(repo).shaHash("a".repeat(40)).build();
        fresh.setId(UUID.randomUUID());
        CommitData already = CommitData.builder().repo(repo).shaHash("b".repeat(40)).build();
        already.setId(UUID.randomUUID());
        when(commits.findHistoricalBacklogWithoutIntent(PageRequest.of(0, 20)))
                .thenReturn(List.of(fresh, already));
        when(intentRepository.findByRepoIdAndShaHash(repo.getId(), fresh.getShaHash()))
                .thenReturn(Optional.empty());
        when(intentRepository.findByRepoIdAndShaHash(repo.getId(), already.getShaHash()))
                .thenReturn(Optional.of(mock(CommitReviewIntent.class)));
        when(intents.enqueueNewCanonicalCommit(repo, fresh)).thenReturn(Optional.of(mock(CommitReviewIntent.class)));
        CommitReviewHistoricalDiscoveryService service = new CommitReviewHistoricalDiscoveryService(
                commits, intents, intentRepository,
                mock(CommitReviewResultRepository.class),
                mock(GitRepoRepository.class),
                mock(CommitReviewWarningPublisher.class)
        );

        assertEquals(1, service.discoverBoundedPage());
        verify(intents, times(1)).enqueueNewCanonicalCommit(repo, fresh);
        verify(intents, never()).enqueueNewCanonicalCommit(repo, already);
        verify(commits, never()).findAll();
    }

    @Test
    void historicalDigestPublishInvokesWarningWritePath() {
        CommitReviewResultRepository results = mock(CommitReviewResultRepository.class);
        GitRepoRepository repos = mock(GitRepoRepository.class);
        CommitReviewWarningPublisher publisher = mock(CommitReviewWarningPublisher.class);
        UUID repoId = UUID.randomUUID();
        Project project = new Project();
        project.setId(UUID.randomUUID());
        GitRepo repo = GitRepo.builder().name("backend").fullName("saga/backend").project(project).build();
        repo.setId(repoId);
        CommitReviewResult row = CommitReviewResult.builder()
                .codeQuality("RISKS")
                .messageQuality("POOR")
                .overallStatus("NEEDS_CHANGES")
                .findingsJson("{\"severity\":\"ERROR\"}")
                .build();
        when(results.findHistoricalRepoIdsInWindow(any(), any())).thenReturn(List.of(repoId));
        when(repos.findById(repoId)).thenReturn(Optional.of(repo));
        when(results.findHistoricalCompletedInWindow(eq(repoId), any(), any())).thenReturn(List.of(row));
        CommitReviewHistoricalDiscoveryService service = new CommitReviewHistoricalDiscoveryService(
                mock(CommitDataRepository.class),
                mock(CommitReviewIntentService.class),
                mock(CommitReviewIntentRepository.class),
                results,
                repos,
                publisher
        );

        service.publishBoundedDigests();

        verify(publisher).publishHistoricalDigest(eq(repo), any());
    }
}
