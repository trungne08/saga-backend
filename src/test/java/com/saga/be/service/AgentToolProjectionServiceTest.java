package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.dto.response.InternalAgentToolResponses;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.repository.DocumentRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;

class AgentToolProjectionServiceTest {

    @Test
    void studentProgressIsBoundToCurrentStudentProfileWithoutInventedScore() {
        UUID projectId = UUID.randomUUID();
        SagaPrincipal student = new SagaPrincipal(
                "student-sub", "student@example.test", "Student",
                ApplicationRole.STUDENT, UUID.randomUUID(), AccountStatus.ACTIVE
        );
        ProjectTaskReadService tasks = mock(ProjectTaskReadService.class);
        when(tasks.getTasks(
                student, projectId, null, null, student.localProfileId(), null,
                "externalKey", "asc", 0, 50
        )).thenReturn(new PageImpl<>(List.of()));
        AgentToolProjectionService service = service(
                mock(ProjectDetailService.class), tasks, mock(CommitReviewContextReader.class)
        );

        InternalAgentToolResponses.StudentProgress result = service.studentProgress(
                student, projectId
        );

        assertEquals(student.localProfileId(), result.studentId());
        assertEquals(0, result.totalAssignedTasks());
        verify(tasks).getTasks(
                student, projectId, null, null, student.localProfileId(), null,
                "externalKey", "asc", 0, 50
        );
    }

    @Test
    void lecturerCannotUseStudentPersonalProgressProjection() {
        SagaPrincipal lecturer = new SagaPrincipal(
                "lecturer-sub", "lecturer@example.test", "Lecturer",
                ApplicationRole.LECTURER, UUID.randomUUID(), AccountStatus.ACTIVE
        );
        AgentToolProjectionService service = service(
                mock(ProjectDetailService.class),
                mock(ProjectTaskReadService.class),
                mock(CommitReviewContextReader.class)
        );

        assertThrows(
                AccessDeniedException.class,
                () -> service.studentProgress(lecturer, UUID.randomUUID())
        );
    }

    @Test
    void commitReviewTargetReauthorizesProjectAndUsesExactLocalCommitIdentity() {
        UUID projectId = UUID.randomUUID();
        String sha = "ABCDEF0123456789ABCDEF0123456789ABCDEF01";
        SagaPrincipal actor = new SagaPrincipal(
                "lecturer-sub", "lecturer@example.test", "Lecturer",
                ApplicationRole.LECTURER, UUID.randomUUID(), AccountStatus.ACTIVE
        );
        ProjectDetailService projects = mock(ProjectDetailService.class);
        CommitReviewContextReader reviews = mock(CommitReviewContextReader.class);
        when(reviews.load(projectId, 42L, sha.toLowerCase())).thenReturn(
                new CommitReviewContextReader.SourceSnapshot(
                        projectId,
                        new CommitReviewContextReader.RepositorySnapshot(
                                UUID.randomUUID(), 42L, "GITHUB", "owner", "repo",
                                "owner/repo", 7L
                        ),
                        new CommitReviewContextReader.CommitSnapshot(
                                UUID.randomUUID(), sha.toLowerCase(), "message",
                                LocalDateTime.now(), 1, 1, 1
                        ),
                        List.of(),
                        false
                )
        );
        AgentToolProjectionService service = service(
                projects,
                mock(ProjectTaskReadService.class),
                reviews
        );

        InternalAgentToolResponses.CommitReviewTarget result = service.commitReviewTarget(
                actor, projectId, 42L, sha
        );

        verify(projects).get(actor, projectId);
        verify(reviews).load(projectId, 42L, sha.toLowerCase());
        assertEquals(projectId, result.projectId());
        assertEquals(42L, result.repositoryId());
        assertEquals(sha.toLowerCase(), result.commitSha());
    }

    private AgentToolProjectionService service(
            ProjectDetailService projects,
            ProjectTaskReadService tasks,
            CommitReviewContextReader reviews
    ) {
        return new AgentToolProjectionService(
                projects,
                tasks,
                mock(TeamContributionService.class),
                mock(GitHubTraceabilityService.class),
                mock(TeamRepository.class),
                mock(TeamMemberRepository.class),
                mock(GitRepoRepository.class),
                mock(DocumentRepository.class),
                reviews
        );
    }
}
