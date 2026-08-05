package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saga.be.dto.response.LecturerAnalyticsResponses;
import com.saga.be.entity.CommitData;
import com.saga.be.entity.Project;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Task;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.DocumentRepository;
import com.saga.be.repository.PeerReviewRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
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
    }

    @Test
    void progressWithNoTasksDoesNotDivideByZeroAndShowsUnclassifiedCount() {
        Fixture f = fixture(true);
        when(f.authorization.requireStudentInCourse(any(), any(), any())).thenReturn(f.membership);
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
        when(f.authorization.requireTeam(any(), any(), any())).thenReturn(f.team);
        assertThrows(ResponseStatusException.class, () -> teamService(f).heatmap(null, f.courseId, f.teamId,
                null, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1)));
    }

    @Test
    void heatmapIncludesBothBoundaryDatesAndStableZeroRows() {
        Fixture f = fixture(true);
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 3);
        when(f.authorization.requireTeam(any(), any(), any())).thenReturn(f.team);
        when(f.commits.aggregateDailyCounts(f.projectId, null, start.atStartOfDay(), end.plusDays(1).atStartOfDay()))
                .thenReturn(List.<Object[]>of(new Object[]{start, 2L}, new Object[]{end, 1L}));
        var rows = teamService(f).heatmap(null, f.courseId, f.teamId, null, start, end).days();
        assertEquals(3, rows.size());
        assertEquals(2, rows.get(0).commits());
        assertEquals(0, rows.get(1).commits());
        assertEquals(1, rows.get(2).commits());
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
    }

    private LecturerTeamAnalyticsQueryService teamService(Fixture f) {
        return new LecturerTeamAnalyticsQueryService(f.authorization, f.members, f.tasks, f.commits,
                f.sprints, f.peers);
    }

    private Fixture fixture(boolean projectPresent) {
        UUID courseId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Team team = id(new Team(), teamId);
        if (projectPresent) {
            Project project = id(new Project(), projectId);
            team.setProject(project);
        }
        Student student = id(new Student(), studentId);
        TeamMember membership = TeamMember.builder().team(team).student(student).build();
        return new Fixture(courseId, teamId, studentId, projectId, team, membership,
                mock(LecturerAnalyticsAuthorizationService.class), mock(TeamMemberRepository.class),
                mock(TaskRepository.class), mock(CommitDataRepository.class), mock(DocumentRepository.class),
                mock(SprintRepository.class), mock(PeerReviewRepository.class));
    }

    private <T> T id(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private record Fixture(UUID courseId, UUID teamId, UUID studentId, UUID projectId, Team team,
                           TeamMember membership, LecturerAnalyticsAuthorizationService authorization,
                           TeamMemberRepository members, TaskRepository tasks, CommitDataRepository commits,
                           DocumentRepository documents, SprintRepository sprints, PeerReviewRepository peers) { }
}
