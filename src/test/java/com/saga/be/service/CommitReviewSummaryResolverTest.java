package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.dto.response.CommitReviewSummary;
import com.saga.be.entity.CommitData;
import com.saga.be.entity.CommitReviewIntent;
import com.saga.be.entity.CommitReviewResult;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.enums.CommitReviewIntentStatus;
import com.saga.be.entity.enums.CommitReviewMode;
import com.saga.be.entity.enums.CommitReviewPriority;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.CommitReviewIntentRepository;
import com.saga.be.repository.CommitReviewResultRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CommitReviewSummaryResolverTest {

    @Test
    void nullWhenNoLocalCanonicalCommitData() {
        Fixture f = new Fixture();
        when(f.commitData.findByRepoIdAndShaHashIn(f.repoId, Set.of("abc"))).thenReturn(List.of());
        var result = f.resolver.resolve(f.repoId, List.of("abc"));
        assertTrue(result.isEmpty());
        verify(f.intents, never()).findByRepoIdAndShaHashIn(any(), any());
    }

    @Test
    void nullWhenCommitDataExistsButNoIntent() {
        Fixture f = new Fixture();
        CommitData commit = commitData("abc");
        when(f.commitData.findByRepoIdAndShaHashIn(f.repoId, Set.of("abc"))).thenReturn(List.of(commit));
        when(f.intents.findByRepoIdAndShaHashIn(f.repoId, Set.of("abc"))).thenReturn(List.of());
        var result = f.resolver.resolve(f.repoId, List.of("abc"));
        assertTrue(result.isEmpty());
        verify(f.results, never()).findByIntentIdIn(any());
    }

    @Test
    void processingStatusesMapExactlyWithNullResult() {
        for (CommitReviewIntentStatus status : List.of(
                CommitReviewIntentStatus.PENDING, CommitReviewIntentStatus.STARTING,
                CommitReviewIntentStatus.STARTED, CommitReviewIntentStatus.RUNNING,
                CommitReviewIntentStatus.WAITING_RETRY)) {
            Fixture f = new Fixture();
            CommitData commit = commitData("abc");
            CommitReviewIntent intent = intent("abc", status);
            when(f.commitData.findByRepoIdAndShaHashIn(f.repoId, Set.of("abc"))).thenReturn(List.of(commit));
            when(f.intents.findByRepoIdAndShaHashIn(f.repoId, Set.of("abc"))).thenReturn(List.of(intent));
            when(f.results.findByIntentIdIn(List.of(intent.getId()))).thenReturn(List.of());

            var summary = f.resolver.resolve(f.repoId, List.of("abc")).get("abc");

            assertEquals(status, summary.intentStatus());
            assertNull(summary.result(), () -> status + " must not fabricate a result");
        }
    }

    @Test
    void completedWithPersistedResultMapsExactSummary() {
        Fixture f = new Fixture();
        CommitData commit = commitData("abc");
        CommitReviewIntent intent = intent("abc", CommitReviewIntentStatus.COMPLETED);
        CommitReviewResult result = result(intent, commit, "TASK_LINKED", "ALIGNED", "PASS", "PASS", true);
        when(f.commitData.findByRepoIdAndShaHashIn(f.repoId, Set.of("abc"))).thenReturn(List.of(commit));
        when(f.intents.findByRepoIdAndShaHashIn(f.repoId, Set.of("abc"))).thenReturn(List.of(intent));
        when(f.results.findByIntentIdIn(List.of(intent.getId()))).thenReturn(List.of(result));

        var summary = f.resolver.resolve(f.repoId, List.of("abc")).get("abc");

        assertEquals(CommitReviewIntentStatus.COMPLETED, summary.intentStatus());
        assertEquals("TASK_LINKED", summary.reviewMode());
        assertEquals("PASS", summary.result().verdict());
        assertEquals("PASS", summary.result().overallStatus());
        assertTrue(summary.result().verdictEligible());
    }

    @Test
    void completedWithoutPersistedResultDoesNotFabricate() {
        Fixture f = new Fixture();
        CommitData commit = commitData("abc");
        CommitReviewIntent intent = intent("abc", CommitReviewIntentStatus.COMPLETED);
        when(f.commitData.findByRepoIdAndShaHashIn(f.repoId, Set.of("abc"))).thenReturn(List.of(commit));
        when(f.intents.findByRepoIdAndShaHashIn(f.repoId, Set.of("abc"))).thenReturn(List.of(intent));
        when(f.results.findByIntentIdIn(List.of(intent.getId()))).thenReturn(List.of());

        var summary = f.resolver.resolve(f.repoId, List.of("abc")).get("abc");

        assertEquals(CommitReviewIntentStatus.COMPLETED, summary.intentStatus());
        assertNull(summary.result(), "must not fabricate PASS/NEEDS_CHANGES when result row is missing");
        assertNull(summary.reviewMode());
    }

    @Test
    void failedNeverMapsToNeedsChangesEvenIfAResultRowExists() {
        Fixture f = new Fixture();
        CommitData commit = commitData("abc");
        CommitReviewIntent intent = intent("abc", CommitReviewIntentStatus.FAILED);
        CommitReviewResult stray = result(intent, commit, "TASK_LINKED", "NEEDS_CHANGES", "NEEDS_CHANGES", "NEEDS_CHANGES", true);
        when(f.commitData.findByRepoIdAndShaHashIn(f.repoId, Set.of("abc"))).thenReturn(List.of(commit));
        when(f.intents.findByRepoIdAndShaHashIn(f.repoId, Set.of("abc"))).thenReturn(List.of(intent));
        when(f.results.findByIntentIdIn(List.of(intent.getId()))).thenReturn(List.of(stray));

        var summary = f.resolver.resolve(f.repoId, List.of("abc")).get("abc");

        assertEquals(CommitReviewIntentStatus.FAILED, summary.intentStatus());
        assertNull(summary.result());
        assertNull(summary.reviewMode());
    }

    @Test
    void cancelledMapsWithNullResult() {
        Fixture f = new Fixture();
        CommitData commit = commitData("abc");
        CommitReviewIntent intent = intent("abc", CommitReviewIntentStatus.CANCELLED);
        when(f.commitData.findByRepoIdAndShaHashIn(f.repoId, Set.of("abc"))).thenReturn(List.of(commit));
        when(f.intents.findByRepoIdAndShaHashIn(f.repoId, Set.of("abc"))).thenReturn(List.of(intent));
        when(f.results.findByIntentIdIn(List.of(intent.getId()))).thenReturn(List.of());

        var summary = f.resolver.resolve(f.repoId, List.of("abc")).get("abc");

        assertEquals(CommitReviewIntentStatus.CANCELLED, summary.intentStatus());
        assertNull(summary.result());
    }

    @Test
    void resolvesTwentyCommitsWithExactlyThreeRepositoryQueries() {
        Fixture f = new Fixture();
        List<String> shas = IntStream.range(0, 20).mapToObj(i -> "sha-" + i).toList();
        List<CommitData> commits = shas.stream().map(this::commitData).toList();
        List<CommitReviewIntent> intents = shas.stream()
                .map(sha -> intent(sha, CommitReviewIntentStatus.COMPLETED))
                .toList();
        when(f.commitData.findByRepoIdAndShaHashIn(any(), any())).thenReturn(commits);
        when(f.intents.findByRepoIdAndShaHashIn(any(), any())).thenReturn(intents);
        when(f.results.findByIntentIdIn(any())).thenReturn(List.of());

        Map<String, CommitReviewSummary> resolved = f.resolver.resolve(f.repoId, shas);

        assertEquals(20, resolved.size());
        verify(f.commitData, times(1)).findByRepoIdAndShaHashIn(any(), any());
        verify(f.intents, times(1)).findByRepoIdAndShaHashIn(any(), any());
        verify(f.results, times(1)).findByIntentIdIn(any());
    }

    private CommitData commitData(String sha) {
        CommitData commit = CommitData.builder().shaHash(sha).build();
        commit.setId(UUID.randomUUID());
        return commit;
    }

    private CommitReviewIntent intent(String sha, CommitReviewIntentStatus status) {
        GitRepo repo = GitRepo.builder().build();
        repo.setId(UUID.randomUUID());
        CommitReviewIntent intent = CommitReviewIntent.builder()
                .repo(repo)
                .commit(commitData(sha))
                .shaHash(sha)
                .reviewMode(CommitReviewMode.LIVE_TASK_AWARE)
                .priority(CommitReviewPriority.HIGH)
                .priorityRank(100)
                .intentStatus(status)
                .build();
        intent.setId(UUID.randomUUID());
        return intent;
    }

    private CommitReviewResult result(
            CommitReviewIntent intent,
            CommitData commit,
            String reviewMode,
            String taskAlignment,
            String verdict,
            String overallStatus,
            boolean verdictEligible
    ) {
        return CommitReviewResult.builder()
                .intent(intent).aiJobId(UUID.randomUUID()).projectId(UUID.randomUUID())
                .repo(intent.getRepo()).commit(commit).shaHash(intent.getShaHash()).policyVersion("v1")
                .reviewMode(reviewMode).traceabilityStatus("VERIFIED").messageQuality("GOOD")
                .codeQuality("GOOD").taskAlignment(taskAlignment).verdictEligible(verdictEligible)
                .verdict(verdict).overallStatus(overallStatus).schemaVersion("commit-review-result-v2")
                .completedAt(LocalDateTime.now()).build();
    }

    private static final class Fixture {
        final CommitDataRepository commitData = mock(CommitDataRepository.class);
        final CommitReviewIntentRepository intents = mock(CommitReviewIntentRepository.class);
        final CommitReviewResultRepository results = mock(CommitReviewResultRepository.class);
        final CommitReviewSummaryResolver resolver = new CommitReviewSummaryResolver(commitData, intents, results);
        final UUID repoId = UUID.randomUUID();
    }
}
