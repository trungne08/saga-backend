package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saga.be.dto.response.LecturerAnalyticsResponses;
import com.saga.be.dto.response.LecturerCourseDashboardResponses;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Project;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Task;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LecturerCourseDashboardQueryServiceTest {

    @Mock LecturerAnalyticsAuthorizationService authorization;
    @Mock TeamRepository teamRepository;
    @Mock TeamMemberRepository teamMemberRepository;
    @Mock SprintRepository sprintRepository;
    @Mock TaskRepository taskRepository;
    @Mock CommitDataRepository commitDataRepository;
    @Mock CourseEarlyWarningQueryService earlyWarningQueryService;

    private LecturerCourseDashboardQueryService service;

    @BeforeEach
    void setUp() {
        service = new LecturerCourseDashboardQueryService(
                authorization,
                teamRepository,
                teamMemberRepository,
                sprintRepository,
                taskRepository,
                commitDataRepository,
                earlyWarningQueryService
        );
    }

    @Test
    void teamsProgressUsesCourseBatchQueriesAndHandlesMissingProjectSprintAndStoryPoints() {
        UUID courseId = UUID.randomUUID();
        UUID activeTeamId = UUID.randomUUID();
        UUID noSprintTeamId = UUID.randomUUID();
        UUID noProjectTeamId = UUID.randomUUID();
        Project activeProject = id(new Project(), UUID.randomUUID());
        Project noSprintProject = id(new Project(), UUID.randomUUID());
        Team activeTeam = id(Team.builder().name("Active Team").project(activeProject).build(), activeTeamId);
        Team noSprintTeam = id(Team.builder().name("No Sprint").project(noSprintProject).build(), noSprintTeamId);
        Team noProjectTeam = id(Team.builder().name("No Project").build(), noProjectTeamId);
        Sprint activeSprint = sprint(activeProject, "active", LocalDateTime.of(2026, 8, 1, 0, 0));
        Sprint closedSprint = sprint(activeProject, "closed", LocalDateTime.of(2026, 7, 1, 0, 0));
        Task done = task(activeProject, activeSprint, TaskStatus.DONE, 5);
        Task inProgress = task(activeProject, activeSprint, TaskStatus.IN_PROGRESS, 3);
        Task doneWithoutPoints = task(activeProject, activeSprint, TaskStatus.DONE, null);
        Task oldSprintTask = task(activeProject, closedSprint, TaskStatus.DONE, 13);

        when(teamRepository.findByCourseIdOrderByNameAscIdAsc(courseId))
                .thenReturn(List.of(activeTeam, noProjectTeam, noSprintTeam));
        when(sprintRepository.findByBoardProjectCourseIdAndDeletedAtIsNullOrderByStartDateAscIdAsc(courseId))
                .thenReturn(List.of(closedSprint, activeSprint));
        when(taskRepository.findByProjectCourseIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(courseId))
                .thenReturn(List.of(done, inProgress, doneWithoutPoints, oldSprintTask));
        when(commitDataRepository.countByProjectForCourse(courseId))
                .thenReturn(List.<Object[]>of(new Object[] {activeProject.getId(), 7L}));

        LecturerCourseDashboardResponses.TeamsProgress response = service.teamsProgress(null, courseId);

        LecturerCourseDashboardResponses.TeamProgress active = team(response, activeTeamId);
        assertEquals(activeSprint.getId(), active.currentSprint().sprintId());
        assertEquals(1, active.activeSprints().size());
        assertEquals(activeSprint.getId(), active.activeSprints().get(0).sprintId());
        assertEquals(3, active.currentSprintTaskCount());
        assertEquals(3, active.activeSprints().get(0).taskCount());
        assertEquals(2, active.currentSprintDoneTaskCount());
        assertEquals(2, active.activeSprints().get(0).doneTaskCount());
        assertEquals(8, active.currentSprintPlannedStoryPoints());
        assertEquals(8, active.activeSprints().get(0).plannedStoryPoints());
        assertEquals(5, active.currentSprintCompletedStoryPoints());
        assertEquals(5, active.activeSprints().get(0).completedStoryPoints());
        assertEquals(1, active.currentSprintTasksWithoutStoryPoints());
        assertEquals(1, active.activeSprints().get(0).tasksWithoutStoryPoints());
        assertEquals(7, active.projectCommitCount());
        assertNull(active.healthStatus());

        LecturerCourseDashboardResponses.TeamProgress noSprint = team(response, noSprintTeamId);
        assertNull(noSprint.currentSprint());
        assertEquals(List.of(), noSprint.activeSprints());
        assertEquals(0, noSprint.currentSprintTaskCount());
        assertEquals(0, noSprint.projectCommitCount());

        LecturerCourseDashboardResponses.TeamProgress noProject = team(response, noProjectTeamId);
        assertNull(noProject.projectId());
        assertNull(noProject.currentSprint());
        assertEquals(List.of(), noProject.activeSprints());
        assertEquals(0, noProject.currentSprintTaskCount());
        assertNull(noProject.healthStatus());

        verify(authorization).requireCourseAccess(null, courseId);
        verify(teamRepository).findByCourseIdOrderByNameAscIdAsc(courseId);
        verify(sprintRepository).findByBoardProjectCourseIdAndDeletedAtIsNullOrderByStartDateAscIdAsc(courseId);
        verify(taskRepository).findByProjectCourseIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(courseId);
        verify(commitDataRepository).countByProjectForCourse(courseId);
    }

    @Test
    void teamsProgressReturnsActiveSprintListWithoutPickingAPrimary() {
        UUID courseId = UUID.randomUUID();
        Project oneActiveProject = id(new Project(), UUID.randomUUID());
        Project twoActiveProject = id(new Project(), UUID.randomUUID());
        Team oneActiveTeam = id(Team.builder().name("One Active").project(oneActiveProject).build(), UUID.randomUUID());
        Team twoActiveTeam = id(Team.builder().name("Two Active").project(twoActiveProject).build(), UUID.randomUUID());
        Sprint onlyActive = sprint(oneActiveProject, "active", LocalDateTime.of(2026, 8, 1, 0, 0), "Only");
        Sprint laterActive = sprint(twoActiveProject, "ACTIVE", LocalDateTime.of(2026, 8, 2, 0, 0), "Later");
        Sprint earlierActive = sprint(twoActiveProject, "active", LocalDateTime.of(2026, 8, 1, 0, 0), "Earlier");
        Sprint deletedActive = sprint(twoActiveProject, "active", LocalDateTime.of(2026, 7, 1, 0, 0), "Deleted");
        deletedActive.setDeletedAt(LocalDateTime.of(2026, 8, 10, 0, 0));
        Task onlyDone = task(oneActiveProject, onlyActive, TaskStatus.DONE, 5);
        Task earlierDone = task(twoActiveProject, earlierActive, TaskStatus.DONE, 8);
        Task laterOpen = task(twoActiveProject, laterActive, TaskStatus.IN_PROGRESS, 3);
        Task laterDone = task(twoActiveProject, laterActive, TaskStatus.DONE, null);
        when(teamRepository.findByCourseIdOrderByNameAscIdAsc(courseId))
                .thenReturn(List.of(oneActiveTeam, twoActiveTeam));
        when(sprintRepository.findByBoardProjectCourseIdAndDeletedAtIsNullOrderByStartDateAscIdAsc(courseId))
                .thenReturn(List.of(laterActive, deletedActive, earlierActive, onlyActive));
        when(taskRepository.findByProjectCourseIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(courseId))
                .thenReturn(List.of(onlyDone, earlierDone, laterOpen, laterDone));
        when(commitDataRepository.countByProjectForCourse(courseId)).thenReturn(List.of());

        LecturerCourseDashboardResponses.TeamsProgress response = service.teamsProgress(null, courseId);

        LecturerCourseDashboardResponses.TeamProgress one = team(response, oneActiveTeam.getId());
        assertEquals(onlyActive.getId(), one.currentSprint().sprintId());
        assertEquals(1, one.activeSprints().size());
        assertEquals(onlyActive.getId(), one.activeSprints().get(0).sprintId());
        assertEquals(1, one.currentSprintTaskCount());
        assertEquals(1, one.currentSprintDoneTaskCount());
        assertEquals(5, one.currentSprintPlannedStoryPoints());
        assertEquals(5, one.currentSprintCompletedStoryPoints());
        assertEquals(0, one.currentSprintTasksWithoutStoryPoints());

        LecturerCourseDashboardResponses.TeamProgress two = team(response, twoActiveTeam.getId());
        assertNull(two.currentSprint());
        assertEquals(0, two.currentSprintTaskCount());
        assertEquals(0, two.currentSprintDoneTaskCount());
        assertEquals(0, two.currentSprintPlannedStoryPoints());
        assertEquals(0, two.currentSprintCompletedStoryPoints());
        assertEquals(0, two.currentSprintTasksWithoutStoryPoints());
        assertEquals(2, two.activeSprints().size());
        assertEquals(List.of(earlierActive.getId(), laterActive.getId()),
                two.activeSprints().stream().map(LecturerCourseDashboardResponses.ActiveSprint::sprintId).toList());
        assertTrue(two.activeSprints().stream().noneMatch(sprint -> deletedActive.getId().equals(sprint.sprintId())));
        LecturerCourseDashboardResponses.ActiveSprint earlier = two.activeSprints().get(0);
        assertEquals("Earlier", earlier.sprintName());
        assertEquals("active", earlier.state());
        assertEquals(1, earlier.taskCount());
        assertEquals(1, earlier.doneTaskCount());
        assertEquals(8, earlier.plannedStoryPoints());
        assertEquals(8, earlier.completedStoryPoints());
        assertEquals(0, earlier.tasksWithoutStoryPoints());
        LecturerCourseDashboardResponses.ActiveSprint later = two.activeSprints().get(1);
        assertEquals("Later", later.sprintName());
        assertEquals("ACTIVE", later.state());
        assertEquals(2, later.taskCount());
        assertEquals(1, later.doneTaskCount());
        assertEquals(3, later.plannedStoryPoints());
        assertEquals(0, later.completedStoryPoints());
        assertEquals(1, later.tasksWithoutStoryPoints());
    }

    @Test
    void contributionSummaryUsesDistinctTeamMembershipAndLeavesUnsupportedAggregatesNull() {
        UUID courseId = UUID.randomUUID();
        Team firstTeam = id(Team.builder().name("A").build(), UUID.randomUUID());
        Team secondTeam = id(Team.builder().name("B").build(), UUID.randomUUID());
        Student firstStudent = id(new Student(), UUID.randomUUID());
        Student secondStudent = id(new Student(), UUID.randomUUID());
        when(teamRepository.findByCourseIdOrderByNameAscIdAsc(courseId))
                .thenReturn(List.of(firstTeam, secondTeam));
        when(teamMemberRepository.findByTeamCourseId(courseId)).thenReturn(List.of(
                TeamMember.builder().team(firstTeam).student(firstStudent).build(),
                TeamMember.builder().team(secondTeam).student(firstStudent).build(),
                TeamMember.builder().team(secondTeam).student(secondStudent).build(),
                TeamMember.builder().team(secondTeam).student(null).build()
        ));

        LecturerCourseDashboardResponses.ContributionSummary response =
                service.contributionSummary(null, courseId);

        assertEquals(2, response.teamCount());
        assertNull(response.totalStudents());
        assertEquals(2, response.totalStudentsWithTeam());
        assertNull(response.totalStudentsWithoutTeam());
        assertNull(response.totalSlicesGenerated());
        verify(authorization).requireCourseAccess(null, courseId);
        verify(teamRepository).findByCourseIdOrderByNameAscIdAsc(courseId);
        verify(teamMemberRepository).findByTeamCourseId(courseId);
        verifyNoInteractions(sprintRepository, taskRepository, commitDataRepository, earlyWarningQueryService);
    }

    @Test
    void trendsAreDeterministicCurrentTaskSnapshotsAndRejectCrossProjectTaskLinks() {
        UUID courseId = UUID.randomUUID();
        Project firstProject = id(new Project(), UUID.randomUUID());
        Project secondProject = id(new Project(), UUID.randomUUID());
        Team firstTeam = id(Team.builder().name("First").project(firstProject).build(), UUID.randomUUID());
        Team secondTeam = id(Team.builder().name("Second").project(secondProject).build(), UUID.randomUUID());
        Sprint later = sprint(firstProject, "active", LocalDateTime.of(2026, 8, 2, 0, 0));
        Sprint earlier = sprint(secondProject, "closed", LocalDateTime.of(2026, 8, 1, 0, 0));
        Sprint undated = sprint(firstProject, "future", null);
        Sprint orphan = sprint(id(new Project(), UUID.randomUUID()), "closed", LocalDateTime.of(2026, 7, 1, 0, 0));
        Task laterDone = task(firstProject, later, TaskStatus.DONE, 5);
        Task laterWithoutPoints = task(firstProject, later, TaskStatus.IN_PROGRESS, null);
        Task inconsistent = task(secondProject, later, TaskStatus.DONE, 99);
        Task earlierTask = task(secondProject, earlier, TaskStatus.IN_PROGRESS, 3);
        when(teamRepository.findByCourseIdOrderByNameAscIdAsc(courseId))
                .thenReturn(List.of(firstTeam, secondTeam));
        when(taskRepository.findByProjectCourseIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(courseId))
                .thenReturn(List.of(laterDone, laterWithoutPoints, inconsistent, earlierTask));
        when(sprintRepository.findByBoardProjectCourseIdAndDeletedAtIsNullOrderByStartDateAscIdAsc(courseId))
                .thenReturn(List.of(undated, later, orphan, earlier));

        LecturerCourseDashboardResponses.Trends response = service.trends(null, courseId);

        assertEquals(List.of(earlier.getId(), later.getId(), undated.getId()),
                response.sprints().stream().map(LecturerCourseDashboardResponses.SprintTrend::sprintId).toList());
        LecturerCourseDashboardResponses.SprintTrend laterRow = response.sprints().get(1);
        assertEquals(2, laterRow.totalTasks());
        assertEquals(1, laterRow.completedTasks());
        assertEquals(5, laterRow.currentPlannedStoryPoints());
        assertEquals(5, laterRow.currentCompletedStoryPoints());
        assertEquals(1, laterRow.tasksWithoutStoryPoints());
        assertNull(laterRow.totalSlicesGenerated());
        response.sprints().forEach(row -> assertNull(row.totalSlicesGenerated()));
        assertEquals(0, response.sprints().get(2).totalTasks());
        verify(authorization).requireCourseAccess(null, courseId);
        verify(teamRepository).findByCourseIdOrderByNameAscIdAsc(courseId);
        verify(taskRepository).findByProjectCourseIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(courseId);
        verify(sprintRepository).findByBoardProjectCourseIdAndDeletedAtIsNullOrderByStartDateAscIdAsc(courseId);
        verifyNoInteractions(teamMemberRepository, commitDataRepository, earlyWarningQueryService);
    }

    @Test
    void atRiskSummaryReusesExactEarlyWarningsWithoutInventingSeverityOrTypes() {
        UUID courseId = UUID.randomUUID();
        UUID firstStudent = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondStudent = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID firstTeam = UUID.randomUUID();
        UUID secondTeam = UUID.randomUUID();
        LocalDateTime detectedAt = LocalDateTime.of(2026, 8, 14, 10, 0);
        List<LecturerAnalyticsResponses.EarlyWarning> warnings = List.of(
                warning(secondStudent, secondTeam, detectedAt),
                warning(firstStudent, firstTeam, detectedAt),
                warning(firstStudent, firstTeam, detectedAt.plusMinutes(1))
        );
        when(earlyWarningQueryService.get(null, courseId))
                .thenReturn(new LecturerAnalyticsResponses.EarlyWarnings(courseId, warnings));

        LecturerCourseDashboardResponses.AtRiskSummary response = service.atRiskSummary(null, courseId);

        assertEquals(3, response.totalWarnings());
        assertEquals(2, response.affectedStudents());
        assertEquals(2, response.affectedTeams());
        assertEquals(Map.of("OVERDUE_TASK", 3L), response.warningDistribution());
        assertEquals(List.of(firstStudent, secondStudent),
                response.students().stream().map(LecturerCourseDashboardResponses.AtRiskStudent::studentId).toList());
        assertEquals(2, response.students().get(0).warningCount());
        assertNull(response.students().get(0).riskLevel());
        assertNull(response.students().get(1).riskLevel());
        verify(earlyWarningQueryService).get(null, courseId);
        verifyNoInteractions(
                authorization,
                teamRepository,
                teamMemberRepository,
                sprintRepository,
                taskRepository,
                commitDataRepository
        );
    }

    private LecturerCourseDashboardResponses.TeamProgress team(
            LecturerCourseDashboardResponses.TeamsProgress response,
            UUID teamId
    ) {
        return response.teams().stream()
                .filter(row -> row.teamId().equals(teamId))
                .findFirst()
                .orElseThrow();
    }

    private Sprint sprint(Project project, String state, LocalDateTime startDate) {
        return sprint(project, state, startDate, "Sprint " + state);
    }

    private Sprint sprint(Project project, String state, LocalDateTime startDate, String name) {
        JiraBoard board = JiraBoard.builder().project(project).build();
        return id(Sprint.builder()
                .board(board)
                .name(name)
                .state(state)
                .startDate(startDate)
                .build(), UUID.randomUUID());
    }

    private Task task(Project project, Sprint sprint, TaskStatus status, Integer storyPoints) {
        return id(Task.builder()
                .project(project)
                .sprint(sprint)
                .status(status)
                .storyPoint(storyPoints)
                .build(), UUID.randomUUID());
    }

    private LecturerAnalyticsResponses.EarlyWarning warning(
            UUID studentId,
            UUID teamId,
            LocalDateTime detectedAt
    ) {
        return new LecturerAnalyticsResponses.EarlyWarning(
                studentId,
                teamId,
                "OVERDUE_TASK",
                null,
                detectedAt,
                "Task is overdue",
                UUID.randomUUID(),
                detectedAt.minusDays(1)
        );
    }

    private <T> T id(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
