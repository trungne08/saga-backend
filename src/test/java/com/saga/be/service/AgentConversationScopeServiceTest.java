package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.AiAgentConversationScope;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Project;
import com.saga.be.entity.Team;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.AiAgentConversationScopeRepository;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentConversationScopeServiceTest {

    @Test
    void studentMustBelongToCourseAndLecturerMustInstructIt() {
        CourseRepository courses = mock(CourseRepository.class);
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        AgentConversationScopeService service = service(courses, memberships);
        UUID courseId = UUID.randomUUID();
        Course course = course(courseId, lecturerId());
        when(courses.findByIdAndDeletedAtIsNull(courseId)).thenReturn(Optional.of(course));
        SagaPrincipal student = actor(ApplicationRole.STUDENT);
        when(memberships.existsByStudentIdAndTeamCourseId(student.localProfileId(), courseId))
                .thenReturn(true);
        assertEquals(courseId, service.requireAccessibleCourse(student, courseId).getId());

        when(memberships.existsByStudentIdAndTeamCourseId(student.localProfileId(), courseId))
                .thenReturn(false);
        IntegrationException forbidden = assertThrows(
                IntegrationException.class,
                () -> service.requireAccessibleCourse(student, courseId)
        );
        assertEquals("AI_AGENT_COURSE_FORBIDDEN", forbidden.getCode());

        SagaPrincipal lecturer = new SagaPrincipal(
                "lecturer-sub", "lecturer@example.test", "Lecturer",
                ApplicationRole.LECTURER, lecturerId(), AccountStatus.ACTIVE
        );
        assertEquals(courseId, service.requireAccessibleCourse(lecturer, courseId).getId());

        SagaPrincipal otherLecturer = actor(ApplicationRole.LECTURER);
        assertThrows(
                IntegrationException.class,
                () -> service.requireAccessibleCourse(otherLecturer, courseId)
        );
    }

    @Test
    void conversationBoundToCourseARejectsCourseBAndDoesNotPickFirst() {
        AiAgentConversationScopeRepository scopes = mock(AiAgentConversationScopeRepository.class);
        CourseRepository courses = mock(CourseRepository.class);
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        AgentConversationScopeService service = new AgentConversationScopeService(
                scopes, courses, memberships, mock(ProjectRepository.class), mock(TeamRepository.class)
        );
        SagaPrincipal student = actor(ApplicationRole.STUDENT);
        UUID conversationId = UUID.randomUUID();
        UUID courseA = UUID.randomUUID();
        UUID courseB = UUID.randomUUID();
        AiAgentConversationScope bound = new AiAgentConversationScope();
        bound.setConversationId(conversationId);
        bound.setCourseId(courseA);
        bound.setOwnerProfileId(student.localProfileId());
        bound.setOwnerApplicationRole(ApplicationRole.STUDENT);
        when(scopes.findByConversationId(conversationId)).thenReturn(Optional.of(bound));
        when(courses.findByIdAndDeletedAtIsNull(courseA)).thenReturn(Optional.of(course(courseA, null)));
        when(memberships.existsByStudentIdAndTeamCourseId(student.localProfileId(), courseA)).thenReturn(true);

        IntegrationException mismatch = assertThrows(
                IntegrationException.class,
                () -> service.resolveForMessage(student, conversationId, courseB)
        );
        assertEquals("AI_AGENT_COURSE_SCOPE_MISMATCH", mismatch.getCode());
        verify(scopes, never()).saveAndFlush(any());
        assertEquals(courseA, service.resolveForMessage(student, conversationId, courseA));
    }

    @Test
    void projectOutsideActiveCourseFailsClosed() {
        ProjectRepository projects = mock(ProjectRepository.class);
        AgentConversationScopeService service = new AgentConversationScopeService(
                mock(AiAgentConversationScopeRepository.class),
                mock(CourseRepository.class),
                mock(TeamMemberRepository.class),
                projects,
                mock(TeamRepository.class)
        );
        UUID courseA = UUID.randomUUID();
        UUID projectB = UUID.randomUUID();
        Course other = course(UUID.randomUUID(), null);
        Project project = Project.builder().course(other).name("Other").build();
        project.setId(projectB);
        when(projects.findWithCourseAndInstructorById(projectB)).thenReturn(Optional.of(project));

        IntegrationException failure = assertThrows(
                IntegrationException.class,
                () -> service.requireProjectInScope(courseA, projectB)
        );
        assertEquals("AI_AGENT_RESOURCE_OUTSIDE_COURSE_SCOPE", failure.getCode());
        service.requireProjectInScope(null, projectB);
    }

    @Test
    void unscopedConversationCanBindOnFirstMessage() {
        AiAgentConversationScopeRepository scopes = mock(AiAgentConversationScopeRepository.class);
        CourseRepository courses = mock(CourseRepository.class);
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        AgentConversationScopeService service = new AgentConversationScopeService(
                scopes, courses, memberships, mock(ProjectRepository.class), mock(TeamRepository.class)
        );
        SagaPrincipal student = actor(ApplicationRole.STUDENT);
        UUID conversationId = UUID.randomUUID();
        UUID courseA = UUID.randomUUID();
        when(scopes.findByConversationId(conversationId)).thenReturn(Optional.empty());
        when(courses.findByIdAndDeletedAtIsNull(courseA)).thenReturn(Optional.of(course(courseA, null)));
        when(memberships.existsByStudentIdAndTeamCourseId(student.localProfileId(), courseA)).thenReturn(true);

        assertEquals(courseA, service.resolveForMessage(student, conversationId, courseA));
        verify(scopes).saveAndFlush(any());
        assertNull(service.resolveForMessage(student, conversationId, null));
    }

    @Test
    void unboundHomeConversationMaySelectCoursePerToolCallWithoutBindingMismatch() {
        AgentConversationScopeService service = service(
                mock(CourseRepository.class), mock(TeamMemberRepository.class)
        );
        UUID courseA = UUID.randomUUID();
        UUID courseB = UUID.randomUUID();
        assertEquals(courseA, service.effectiveCourseId(null, courseA));
        assertEquals(courseB, service.effectiveCourseId(null, courseB));
        assertNull(service.effectiveCourseId(null, null));
        assertEquals(courseA, service.effectiveCourseId(courseA, null));
        assertEquals(courseA, service.effectiveCourseId(courseA, courseA));
        IntegrationException mismatch = assertThrows(
                IntegrationException.class,
                () -> service.effectiveCourseId(courseA, courseB)
        );
        assertEquals("AI_AGENT_COURSE_SCOPE_MISMATCH", mismatch.getCode());
    }

    private AgentConversationScopeService service(
            CourseRepository courses, TeamMemberRepository memberships
    ) {
        return new AgentConversationScopeService(
                mock(AiAgentConversationScopeRepository.class),
                courses,
                memberships,
                mock(ProjectRepository.class),
                mock(TeamRepository.class)
        );
    }

    private Course course(UUID courseId, UUID instructorId) {
        Lecturer instructor = null;
        if (instructorId != null) {
            instructor = Lecturer.builder().build();
            instructor.setId(instructorId);
        }
        Course course = Course.builder().courseCode("SE-A").name("Course A").instructor(instructor).build();
        course.setId(courseId);
        return course;
    }

    private UUID lecturerId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000222");
    }

    private SagaPrincipal actor(ApplicationRole role) {
        return new SagaPrincipal(
                role.name().toLowerCase() + "-sub",
                role.name().toLowerCase() + "@example.test",
                role.name(), role, UUID.randomUUID(), AccountStatus.ACTIVE
        );
    }
}
