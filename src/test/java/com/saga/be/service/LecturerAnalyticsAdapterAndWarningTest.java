package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.dto.response.TeamContributionEvaluationResponse;
import com.saga.be.dto.response.TeamContributionMemberResponse;
import com.saga.be.entity.Project;
import com.saga.be.entity.Student;
import com.saga.be.entity.Task;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LecturerAnalyticsAdapterAndWarningTest {

    @Test
    void contributionAdapterReturnsExistingMemberAggregateWithoutRecalculation() {
        UUID courseId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Team team = id(new Team(), teamId);
        team.setProject(id(new Project(), projectId));
        TeamMember membership = TeamMember.builder().team(team).student(id(new Student(), studentId)).build();
        LecturerAnalyticsAuthorizationService authorization = mock(LecturerAnalyticsAuthorizationService.class);
        TeamContributionService contribution = mock(TeamContributionService.class);
        TeamContributionMemberResponse aggregate = new TeamContributionMemberResponse(studentId, "Student", "SE001",
                1, 2, 3, 4, 5, 6, 1, 7, 8, 9, 2, List.of(), List.of());
        when(authorization.requireStudentInCourse(any(), any(), any())).thenReturn(membership);
        when(contribution.evaluate(teamId)).thenReturn(new TeamContributionEvaluationResponse(
                teamId, projectId, LocalDateTime.now(), List.of(aggregate)));

        var response = new LecturerContributionQueryService(authorization, contribution)
                .get(null, courseId, studentId);

        assertSame(aggregate, response.currentAggregate());
        verify(contribution).evaluate(teamId);
    }

    @Test
    void warningUsesOnlyOverdueNonDoneTaskAndDoesNotInventSeverity() {
        UUID courseId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Project project = id(new Project(), projectId);
        Team team = id(new Team(), teamId);
        team.setProject(project);
        Student student = id(new Student(), studentId);
        TeamMember membership = TeamMember.builder().team(team).student(student).build();
        Task overdue = id(Task.builder().project(project).assignee(student).status(TaskStatus.IN_PROGRESS)
                .dueDate(LocalDateTime.now().minusDays(1)).build(), UUID.randomUUID());
        Task done = id(Task.builder().project(project).assignee(student).status(TaskStatus.DONE)
                .dueDate(LocalDateTime.now().minusDays(2)).build(), UUID.randomUUID());
        LecturerAnalyticsAuthorizationService authorization = mock(LecturerAnalyticsAuthorizationService.class);
        TeamMemberRepository members = mock(TeamMemberRepository.class);
        TaskRepository tasks = mock(TaskRepository.class);
        when(members.findByTeamCourseId(courseId)).thenReturn(List.of(membership));
        when(tasks.findByProjectCourseId(courseId)).thenReturn(List.of(overdue, done));

        var response = new CourseEarlyWarningQueryService(authorization, members, tasks).get(null, courseId);

        assertEquals(1, response.warnings().size());
        assertEquals("OVERDUE_TASK", response.warnings().get(0).warningType());
        assertNull(response.warnings().get(0).severity());
    }

    private <T> T id(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
