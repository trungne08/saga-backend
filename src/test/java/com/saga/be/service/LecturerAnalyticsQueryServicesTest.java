package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saga.be.dto.response.LecturerAnalyticsResponses;
import com.saga.be.entity.CommitData;
import com.saga.be.entity.Comment;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.Project;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.PeerReview;
import com.saga.be.entity.Student;
import com.saga.be.entity.Task;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.entity.enums.GraphProcessingKind;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.CommentRepository;
import com.saga.be.repository.DocumentRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.PeerReviewRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.integration.provider.GitHubProviderClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class LecturerAnalyticsQueryServicesTest {

    @Test
    void teamDetailKeepsProjectNullable() {
        Fixture f = fixture(false);
        when(f.authorization.requireTeam(any(), any(), any())).thenReturn(f.team);
        when(f.members.findByTeamId(f.teamId, PageRequest.of(0, 20)))
                .thenReturn(org.springframework.data.domain.Page.empty(PageRequest.of(0, 20)));
        LecturerTeamAnalyticsQueryService service = teamService(f);
        LecturerAnalyticsResponses.TeamDetail response = service.detail(null, f.courseId, f.teamId,
                PageRequest.of(0, 20));
        assertNull(response.project());
        verifyNoInteractions(f.repositories);
    }

    @Test
    void teamDetailReturnsEmptyRepositoriesWhenProjectHasNoGitHubRepository() {
        Fixture f = fixture(true);
        when(f.authorization.requireTeam(any(), any(), any())).thenReturn(f.team);
        when(f.members.findByTeamId(f.teamId, PageRequest.of(0, 20)))
                .thenReturn(org.springframework.data.domain.Page.empty(PageRequest.of(0, 20)));
        when(f.repositories.findByProjectIdAndRepositoryIdIsNotNullOrderByFullNameAscRepositoryIdAsc(
                f.projectId)).thenReturn(List.of());

        LecturerAnalyticsResponses.TeamDetail response = teamService(f).detail(
                null, f.courseId, f.teamId, PageRequest.of(0, 20));

        assertTrue(response.project().repositories().isEmpty());
    }

    @Test
    void teamDetailReturnsOneDownstreamRepositoryReference() {
        Fixture f = fixture(true);
        when(f.authorization.requireTeam(any(), any(), any())).thenReturn(f.team);
        when(f.members.findByTeamId(f.teamId, PageRequest.of(0, 20)))
                .thenReturn(org.springframework.data.domain.Page.empty(PageRequest.of(0, 20)));
        when(f.repositories.findByProjectIdAndRepositoryIdIsNotNullOrderByFullNameAscRepositoryIdAsc(
                f.projectId)).thenReturn(List.of(repository(101L, "saga/backend")));

        var reference = teamService(f).detail(null, f.courseId, f.teamId, PageRequest.of(0, 20))
                .project().repositories().get(0);

        assertEquals(101L, reference.repositoryId());
        assertEquals("saga/backend", reference.repositoryName());
    }

    @Test
    void teamDetailReturnsEveryRepositoryWithoutPickingTheFirst() {
        Fixture f = fixture(true);
        when(f.authorization.requireTeam(any(), any(), any())).thenReturn(f.team);
        when(f.members.findByTeamId(f.teamId, PageRequest.of(0, 20)))
                .thenReturn(org.springframework.data.domain.Page.empty(PageRequest.of(0, 20)));
        when(f.repositories.findByProjectIdAndRepositoryIdIsNotNullOrderByFullNameAscRepositoryIdAsc(
                f.projectId)).thenReturn(List.of(
                        repository(101L, "saga/backend"),
                        repository(202L, "saga/frontend")
                ));

        var references = teamService(f).detail(null, f.courseId, f.teamId, PageRequest.of(0, 20))
                .project().repositories();

        assertEquals(List.of(101L, 202L), references.stream()
                .map(reference -> reference.repositoryId()).toList());
        assertEquals(2, references.size());
    }

    @Test
    void teamDetailHasNoGitHubProviderDependency() {
        assertTrue(java.util.Arrays.stream(LecturerTeamAnalyticsQueryService.class.getDeclaredFields())
                .noneMatch(field -> GitHubProviderClient.class.isAssignableFrom(field.getType())));
    }

    @Test
    void progressWithNoTasksDoesNotDivideByZeroAndShowsUnclassifiedCount() {
        Fixture f = fixture(true);
        when(f.authorization.requireStudentProgressAccess(any(), any(), any())).thenReturn(f.membership);
        when(f.tasks.findByProjectIdAndAssigneeId(f.projectId, f.studentId)).thenReturn(List.of());
        LecturerStudentAnalyticsQueryService service = new LecturerStudentAnalyticsQueryService(
                f.authorization, f.tasks, f.commits, f.documents);
        LecturerAnalyticsResponses.StudentProgress response = service.progress(null, f.courseId, f.studentId);
        assertEquals(0.0, response.overallCompletionRate());
        assertEquals(0, response.totalTasks());
    }

    @Test
    void velocityUsesDoneAndExcludesNullStoryPointsFromPointTotals() {
        Fixture f = fixture(true);
        Sprint sprint = id(new Sprint(), UUID.randomUUID());
        Task done = Task.builder().sprint(sprint).status(TaskStatus.DONE).type(TaskType.BUG).storyPoint(5).build();
        Task nullPoint = Task.builder().sprint(sprint).status(TaskStatus.IN_PROGRESS).type(TaskType.TASK).build();
        when(f.authorization.requireTeam(any(), any(), any())).thenReturn(f.team);
        when(f.tasks.findByProjectId(f.projectId)).thenReturn(List.of(done, nullPoint));
        when(f.sprints.findByBoardProjectIdOrderByStartDateAsc(f.projectId)).thenReturn(List.of(sprint));
        LecturerAnalyticsResponses.SprintVelocityItem row = teamService(f).velocity(null, f.courseId, f.teamId)
                .sprints().get(0);
        assertEquals(5, row.currentPlannedPoints());
        assertEquals(5, row.completedPoints());
        assertEquals(1, row.tasksWithoutStoryPoints());
        assertEquals(1, row.bugsCount());
    }

    @Test
    void heatmapRejectsInvalidDateRangeAndDoesNotInventLevels() {
        Fixture f = fixture(false);
        when(f.authorization.requireGraphReadAccess(any(), any(), any())).thenReturn(f.team);
        assertThrows(ResponseStatusException.class, () -> teamService(f).heatmap(null, f.courseId, f.teamId,
                null, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1)));
    }

    @Test
    void heatmapIncludesBothBoundaryDatesAndStableZeroRows() {
        Fixture f = fixture(true);
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 3);
        when(f.authorization.requireGraphReadAccess(any(), any(), any())).thenReturn(f.team);
        when(f.members.findByTeamId(f.teamId)).thenReturn(List.of(f.membership));
        when(f.commits.aggregateDailyCountsByProjectAndAuthorIds(any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{f.studentId, start, 2L},
                        new Object[]{f.studentId, end, 1L}
                ));
        when(f.peers.aggregateDailyCountsByProjectAndReviewerIds(any(), any(), any(), any())).thenReturn(List.of());
        when(f.comments.aggregateDailyCountsByProjectAndAuthorIds(any(), any(), any(), any())).thenReturn(List.of());
        when(f.documents.aggregateDailyCountsByProjectAndAuthorIds(any(), any(), any(), any())).thenReturn(List.of());
        when(f.tasks.aggregateDailyCountsByProjectAndAssigneeIds(any(), any(), any(), any())).thenReturn(List.of());
        var rows = teamService(f).heatmap(null, f.courseId, f.teamId, null, start, end).days();
        assertEquals(3, rows.size());
        assertEquals(2, rows.get(0).commits());
        assertEquals(0, rows.get(1).commits());
        assertEquals(1, rows.get(2).commits());
    }

    @Test
    void heatmapAggregatesMultipleSourcesIntoRowsAndScores() {
        Fixture f = fixture(true);
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 2);
        when(f.authorization.requireGraphReadAccess(any(), any(), any())).thenReturn(f.team);
        when(f.members.findByTeamId(f.teamId)).thenReturn(List.of(f.membership));
        when(f.commits.aggregateDailyCountsByProjectAndAuthorIds(any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{f.studentId, start, 2L}
                ));
        when(f.peers.aggregateDailyCountsByProjectAndReviewerIds(any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{f.studentId, start, 1L}
                ));
        when(f.comments.aggregateDailyCountsByProjectAndAuthorIds(any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{f.studentId, end, 3L}
                ));
        when(f.documents.aggregateDailyCountsByProjectAndAuthorIds(any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{f.studentId, end, 4L}
                ));
        when(f.tasks.aggregateDailyCountsByProjectAndAssigneeIds(any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{f.studentId, end, 5L}
                ));

        LecturerAnalyticsResponses.ActivityHeatmap heatmap = teamService(f)
                .heatmap(null, f.courseId, f.teamId, null, start, end);

        assertEquals(1, heatmap.students().size());
        assertEquals(15, heatmap.students().get(0).totalActivities());
        assertEquals(25, heatmap.students().get(0).totalScore());
        assertEquals(2, heatmap.students().get(0).cells().size());
        assertEquals(12, heatmap.days().get(1).totalActivities());
        assertEquals(17, heatmap.days().get(1).totalScore());
    }

    @Test
    void overviewAggregatesTeamActivityAcrossAllMembers() {
        Fixture f = fixture(true);
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 2);
        when(f.authorization.requireGraphReadAccess(any(), any(), any())).thenReturn(f.team);
        when(f.members.findDistinctStudentIdsByTeamId(f.teamId)).thenReturn(List.of(f.studentId));
        when(f.commits.aggregateDailyCountsByProjectAndAuthorIds(any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{f.studentId, start, 2L}
                ));
        when(f.peers.aggregateDailyCountsByProjectAndReviewerIds(any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{f.studentId, start, 1L}
                ));
        when(f.comments.aggregateDailyCountsByProjectAndAuthorIds(any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{f.studentId, end, 3L}
                ));
        when(f.documents.aggregateDailyCountsByProjectAndAuthorIds(any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{f.studentId, end, 4L}
                ));
        when(f.tasks.aggregateDailyCountsByProjectAndAssigneeIds(any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{f.studentId, end, 5L}
                ));

        LecturerAnalyticsResponses.ActivityOverview overview = teamService(f)
                .overview(null, f.courseId, f.teamId, start, end);

        assertEquals(2, overview.days().size());
        assertEquals(8, overview.days().get(0).totalScore());
        assertEquals(3, overview.days().get(0).totalActivities());
        assertEquals(17, overview.days().get(1).totalScore());
        assertEquals(25, overview.totals().totalScore());
        assertEquals(15, overview.totals().totalActivities());
    }

    @Test
    void studentInteractionsAggregateReviewCommentAssignmentAndCommitEdges() {
        Fixture f = fixture(true);
        UUID studentBId = UUID.randomUUID();
        UUID studentCId = UUID.randomUUID();
        Student studentA = f.membership.getStudent();
        Student studentB = id(new Student(), studentBId);
        Student studentC = id(new Student(), studentCId);
        TeamMember memberA = TeamMember.builder().team(f.team).student(studentA).build();
        TeamMember memberB = TeamMember.builder().team(f.team).student(studentB).build();
        TeamMember memberC = TeamMember.builder().team(f.team).student(studentC).build();
        PeerReview review = PeerReview.builder().reviewer(studentA).reviewee(studentB).build();
        Comment parent = Comment.builder().author(studentA).build();
        Comment reply = Comment.builder().author(studentB).parentComment(parent).build();
        Task assignedTask = Task.builder().project(f.team.getProject()).reporter(studentA).assignee(studentC).build();
        CommitData commit = CommitData.builder().task(assignedTask).author(studentB).build();

        when(f.authorization.requireGraphReadAccess(any(), any(), any())).thenReturn(f.team);
        when(f.members.findByTeamIdAndStudentId(f.teamId, f.studentId)).thenReturn(java.util.Optional.of(f.membership));
        when(f.members.findByTeamId(f.teamId)).thenReturn(List.of(memberA, memberB, memberC));
        when(f.peers.findBySprintBoardProjectIdOrderByCreatedAtAscIdAsc(f.projectId)).thenReturn(List.of(review));
        when(f.comments.findByTaskProjectIdOrderByCreatedAtAscIdAsc(f.projectId)).thenReturn(List.of(reply));
        when(f.comments.findByPullRequestRepoProjectIdOrderByCreatedAtAscIdAsc(f.projectId)).thenReturn(List.of());
        when(f.comments.findByGitIssueRepoProjectIdOrderByCreatedAtAscIdAsc(f.projectId)).thenReturn(List.of());
        when(f.tasks.findByProjectId(f.projectId)).thenReturn(List.of(assignedTask));
        when(f.commits.findByProjectId(f.projectId)).thenReturn(List.of(commit));

        LecturerAnalyticsResponses.StudentInteractionGraph graph = teamService(f)
                .studentInteractions(null, f.courseId, f.teamId, f.studentId);

        assertEquals(3, graph.nodes().size());
        assertEquals(f.studentId, graph.nodes().get(0).studentId());
        assertEquals(3, graph.nodes().get(0).degree());
        assertEquals(4, graph.edges().size());
        assertTrue(graph.edges().stream().anyMatch(edge -> "ASSIGNED_TO".equals(edge.sourceType())));
        assertTrue(graph.edges().stream().anyMatch(edge -> "COMMENTED_ON".equals(edge.sourceType())));
        assertTrue(graph.edges().stream().anyMatch(edge -> "COLLABORATED_WITH".equals(edge.sourceType())));
        assertTrue(graph.edges().stream().anyMatch(edge -> "REVIEWED".equals(edge.sourceType())));
    }

    @Test
    void burndownUsesCreatedAndResolvedDatesToTrackRemainingWork() {
        Fixture f = fixture(true);
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = id(new Sprint(), sprintId);
        sprint.setStartDate(LocalDateTime.of(2026, 8, 1, 0, 0));
        sprint.setEndDate(LocalDateTime.of(2026, 8, 3, 23, 59));
        Task doneTask = Task.builder().sprint(sprint).status(TaskStatus.DONE).build();
        ReflectionTestUtils.setField(doneTask, "createdAt", LocalDateTime.of(2026, 8, 1, 8, 0));
        ReflectionTestUtils.setField(doneTask, "resolvedAt", LocalDateTime.of(2026, 8, 2, 10, 0));
        Task openTask = Task.builder().sprint(sprint).status(TaskStatus.IN_PROGRESS).build();
        ReflectionTestUtils.setField(openTask, "createdAt", LocalDateTime.of(2026, 8, 2, 11, 0));

        when(f.authorization.requireGraphReadAccess(any(), any(), any())).thenReturn(f.team);
        when(f.sprints.findByIdAndBoardProjectIdAndDeletedAtIsNull(sprintId, f.projectId))
                .thenReturn(java.util.Optional.of(sprint));
        when(f.tasks.findByProjectId(f.projectId)).thenReturn(List.of(doneTask, openTask));

        LecturerAnalyticsResponses.BurndownChart chart = teamService(f)
                .burndown(null, f.courseId, f.teamId, sprintId);

        assertEquals(2, chart.totalScope());
        assertEquals(3, chart.points().size());
        assertEquals(2, chart.points().get(0).idealRemaining());
        assertEquals(1, chart.points().get(0).actualRemaining());
        assertEquals(0, chart.points().get(0).doneCount());
        assertEquals(1, chart.points().get(1).idealRemaining());
        assertEquals(1, chart.points().get(1).actualRemaining());
        assertEquals(1, chart.points().get(1).doneCount());
        assertEquals(0, chart.points().get(2).idealRemaining());
        assertEquals(1, chart.points().get(2).actualRemaining());
        assertEquals(1, chart.points().get(2).doneCount());
    }

    @Test
    void activitiesUseDeterministicSourceIdSecondaryOrder() {
        Fixture f = fixture(true);
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 1, 12, 0);
        CommitData second = id(CommitData.builder().timestamp(occurredAt).message("same").build(), secondId);
        CommitData first = id(CommitData.builder().timestamp(occurredAt).message("same").build(), firstId);
        when(f.authorization.requireStudentInCourse(any(), any(), any())).thenReturn(f.membership);
        when(f.commits.findByAuthorIdAndRepoProjectIdOrderByTimestampDescIdDesc(
                any(), any(), any())).thenReturn(List.of(second, first));
        when(f.documents.findByProjectIdAndAuthorIdOrderByCreatedAtDescIdDesc(any(), any(), any()))
                .thenReturn(List.of());
        var rows = new LecturerStudentAnalyticsQueryService(f.authorization, f.tasks, f.commits, f.documents)
                .activities(null, f.courseId, f.studentId, PageRequest.of(0, 10)).activities().getContent();
        assertEquals(firstId, rows.get(0).sourceId());
        assertEquals(secondId, rows.get(1).sourceId());
    }

    @Test
    void activitiesOmitRecordsWithoutTimestamp() {
        Fixture f = fixture(true);
        CommitData missingTimestamp = id(CommitData.builder().message("missing timestamp").build(), UUID.randomUUID());
        when(f.authorization.requireStudentInCourse(any(), any(), any())).thenReturn(f.membership);
        when(f.commits.findByAuthorIdAndRepoProjectIdOrderByTimestampDescIdDesc(any(), any(), any()))
                .thenReturn(List.of(missingTimestamp));
        when(f.documents.findByProjectIdAndAuthorIdOrderByCreatedAtDescIdDesc(any(), any(), any()))
                .thenReturn(List.of());

        var page = new LecturerStudentAnalyticsQueryService(f.authorization, f.tasks, f.commits, f.documents)
                .activities(null, f.courseId, f.studentId, PageRequest.of(0, 10)).activities();

        assertEquals(0, page.getTotalElements());
        assertEquals(0, page.getNumberOfElements());
    }

    @Test
    void interactionDoesNotCreateEdgesWithoutRecordedInteraction() {
        Fixture f = fixture(true);
        when(f.authorization.requireTeam(any(), any(), any())).thenReturn(f.team);
        when(f.members.findByTeamId(f.teamId)).thenReturn(List.of(f.membership));
        when(f.peers.findByRevieweeIdInAndSprintBoardProjectId(any(), any())).thenReturn(List.of());
        assertEquals(0, teamService(f).interactions(null, f.courseId, f.teamId).edges().size());
        verify(f.recorder).record(GraphProcessingKind.INTERACTION, f.courseId, f.teamId, null, 1, 0);
    }

    private LecturerTeamAnalyticsQueryService teamService(Fixture f) {
        return new LecturerTeamAnalyticsQueryService(f.authorization, f.members, f.repositories, f.tasks, f.commits,
                f.documents, f.comments, f.sprints, f.peers, f.recorder);
    }

    private Fixture fixture(boolean projectPresent) {
        UUID courseId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Team team = id(new Team(), teamId);
        if (projectPresent) {
            Project project = id(new Project(), projectId);
            project.setName("Project");
            team.setProject(project);
        }
        Student student = id(new Student(), studentId);
        TeamMember membership = TeamMember.builder().team(team).student(student).build();
        return new Fixture(courseId, teamId, studentId, projectId, team, membership,
                mock(LecturerAnalyticsAuthorizationService.class), mock(TeamMemberRepository.class),
                mock(GitRepoRepository.class),
                mock(TaskRepository.class), mock(CommitDataRepository.class), mock(DocumentRepository.class),
                mock(CommentRepository.class), mock(SprintRepository.class), mock(PeerReviewRepository.class),
                mock(GraphProcessingRunRecorder.class));
    }

    private GitRepo repository(long repositoryId, String fullName) {
        return GitRepo.builder()
                .repositoryId(repositoryId)
                .fullName(fullName)
                .name(fullName.substring(fullName.indexOf('/') + 1))
                .build();
    }

    private <T> T id(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private record Fixture(UUID courseId, UUID teamId, UUID studentId, UUID projectId, Team team,
                           TeamMember membership, LecturerAnalyticsAuthorizationService authorization,
                           TeamMemberRepository members, GitRepoRepository repositories,
                           TaskRepository tasks, CommitDataRepository commits,
                           DocumentRepository documents, CommentRepository comments, SprintRepository sprints,
                           PeerReviewRepository peers, GraphProcessingRunRecorder recorder) { }
}
