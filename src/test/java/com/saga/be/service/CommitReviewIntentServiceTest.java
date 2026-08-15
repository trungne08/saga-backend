package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.CommitData;
import com.saga.be.entity.CommitReviewIntent;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.enums.CommitReviewIntentStatus;
import com.saga.be.entity.enums.CommitReviewMode;
import com.saga.be.entity.enums.CommitReviewPriority;
import com.saga.be.repository.CommitReviewIntentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommitReviewIntentServiceTest {

    @Mock private CommitReviewIntentRepository intents;
    @Mock private org.springframework.context.ApplicationEventPublisher events;
    private CommitReviewIntentService service;

    @BeforeEach
    void setUp() {
        service = new CommitReviewIntentService(intents, events);
    }

    @Test
    void webhookReconcileAndManualSyncConvergeOnOneIntent() {
        GitRepo repo = repo();
        CommitData commit = commit(repo, "abc", LocalDateTime.parse("2026-08-02T00:00:00"));
        CommitReviewIntent existing = CommitReviewIntent.builder().shaHash("abc").build();
        when(intents.findByRepoIdAndShaHash(repo.getId(), "abc")).thenReturn(Optional.of(existing));

        Optional<CommitReviewIntent> first = service.enqueueNewCanonicalCommit(repo, commit);
        Optional<CommitReviewIntent> second = service.enqueueNewCanonicalCommit(repo, commit);
        Optional<CommitReviewIntent> third = service.enqueueNewCanonicalCommit(repo, commit);

        assertSame(existing, first.orElseThrow());
        assertSame(existing, second.orElseThrow());
        assertSame(existing, third.orElseThrow());
        verify(intents, never()).saveAndFlush(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void liveCommitIsHighAndHistoricalIsLow() {
        GitRepo repo = repo();
        when(intents.findByRepoIdAndShaHash(any(), any())).thenReturn(Optional.empty());
        when(intents.saveAndFlush(any())).thenAnswer(invocation -> {
            CommitReviewIntent intent = invocation.getArgument(0);
            if (intent.getId() == null) {
                ReflectionTestUtils.setField(intent, "id", UUID.randomUUID());
            }
            return intent;
        });

        CommitReviewIntent live = service.enqueueNewCanonicalCommit(
                repo, commit(repo, "live", LocalDateTime.parse("2026-08-02T00:00:00"))).orElseThrow();
        CommitReviewIntent historical = service.enqueueNewCanonicalCommit(
                repo, commit(repo, "old", LocalDateTime.parse("2026-07-01T00:00:00"))).orElseThrow();

        assertEquals(CommitReviewMode.LIVE_TASK_AWARE, live.getReviewMode());
        assertEquals(CommitReviewPriority.HIGH, live.getPriority());
        assertEquals(CommitReviewMode.HISTORICAL_LIGHT, historical.getReviewMode());
        assertEquals(CommitReviewPriority.LOW, historical.getPriority());
        verify(events, org.mockito.Mockito.times(2)).publishEvent(any(CommitReviewIntentQueued.class));
    }

    @Test
    void historicalBacklogDoesNotStarveLiveWork() {
        CommitReviewIntent olderLow = intent(0, "2026-08-01T00:00:00");
        CommitReviewIntent newerHigh = intent(1, "2026-08-02T00:00:00");
        when(intents.findReady(any(), any(Pageable.class))).thenReturn(List.of(newerHigh, olderLow));

        List<CommitReviewIntent> work = service.nextWorkAvoidingHistoricalStarvation();

        assertEquals(List.of(newerHigh, olderLow), work);
        assertEquals(1, work.get(0).getPriorityRank());
    }

    private GitRepo repo() {
        GitRepo repo = new GitRepo();
        ReflectionTestUtils.setField(repo, "id", UUID.randomUUID());
        repo.setReviewCutoverAt(LocalDateTime.parse("2026-08-01T00:00:00"));
        return repo;
    }

    private CommitData commit(GitRepo repo, String sha, LocalDateTime timestamp) {
        CommitData commit = new CommitData();
        ReflectionTestUtils.setField(commit, "id", UUID.randomUUID());
        commit.setRepo(repo);
        commit.setShaHash(sha);
        commit.setTimestamp(timestamp);
        return commit;
    }

    private CommitReviewIntent intent(int rank, String createdAt) {
        CommitReviewIntent intent = new CommitReviewIntent();
        ReflectionTestUtils.setField(intent, "id", UUID.randomUUID());
        intent.setPriorityRank(rank);
        intent.setIntentStatus(CommitReviewIntentStatus.PENDING);
        ReflectionTestUtils.setField(intent, "createdAt", LocalDateTime.parse(createdAt));
        return intent;
    }
}
