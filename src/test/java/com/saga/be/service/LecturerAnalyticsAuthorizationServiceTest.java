package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LecturerAnalyticsAuthorizationServiceTest {
    @Mock CourseRepository courseRepository;
    @Mock TeamRepository teamRepository;
    @Mock TeamMemberRepository teamMemberRepository;
    LecturerAnalyticsAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new LecturerAnalyticsAuthorizationService(courseRepository, teamRepository, teamMemberRepository);
    }

    @Test
    void adminMayAccessAnyExistingCourse() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        assertSame(course, service.requireCourseAccess(principal(ApplicationRole.ADMIN, UUID.randomUUID()), courseId));
    }

    @Test
    void assignedLecturerMayAccessOwnCourseButAnotherLecturerIsForbidden() {
        UUID courseId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Lecturer lecturer = new Lecturer();
        ReflectionTestUtils.setField(lecturer, "id", ownerId);
        Course course = new Course();
        course.setInstructor(lecturer);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        assertSame(course, service.requireCourseAccess(principal(ApplicationRole.LECTURER, ownerId), courseId));
        assertThrows(AccessDeniedException.class,
                () -> service.requireCourseAccess(principal(ApplicationRole.LECTURER, UUID.randomUUID()), courseId));
    }

    @Test
    void studentIsAlwaysForbidden() {
        UUID courseId = UUID.randomUUID();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(new Course()));
        assertThrows(AccessDeniedException.class,
                () -> service.requireCourseAccess(principal(ApplicationRole.STUDENT, UUID.randomUUID()), courseId));
    }

    @Test
    void missingCourseIsNotFound() {
        UUID courseId = UUID.randomUUID();
        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> service.requireCourseAccess(principal(ApplicationRole.ADMIN, UUID.randomUUID()), courseId));
    }

    @Test
    void teamFromAnotherCourseIsNotExposed() {
        UUID requestedCourseId = UUID.randomUUID();
        UUID actualCourseId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Course requested = id(new Course(), requestedCourseId);
        Course actual = id(new Course(), actualCourseId);
        Team team = new Team();
        team.setCourse(actual);
        when(courseRepository.findById(requestedCourseId)).thenReturn(Optional.of(requested));
        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(Optional.of(team));
        assertThrows(ResponseStatusException.class,
                () -> service.requireTeam(
                        principal(ApplicationRole.ADMIN, UUID.randomUUID()), requestedCourseId, teamId));
    }

    @Test
    void studentOutsideCourseIsNotFound() {
        UUID courseId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(new Course()));
        when(teamMemberRepository.findByStudentIdAndTeamCourseId(studentId, courseId))
                .thenReturn(java.util.List.of());
        assertThrows(ResponseStatusException.class,
                () -> service.requireStudentInCourse(
                        principal(ApplicationRole.ADMIN, UUID.randomUUID()), courseId, studentId));
    }

    @Test
    void duplicateStudentMembershipInCourseIsConflict() {
        UUID courseId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(new Course()));
        when(teamMemberRepository.findByStudentIdAndTeamCourseId(studentId, courseId))
                .thenReturn(java.util.List.of(new TeamMember(), new TeamMember()));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.requireStudentInCourse(
                        principal(ApplicationRole.ADMIN, UUID.randomUUID()), courseId, studentId));

        org.junit.jupiter.api.Assertions.assertEquals(409, exception.getStatusCode().value());
    }

    private <T> T id(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private SagaPrincipal principal(ApplicationRole role, UUID id) {
        return new SagaPrincipal("subject", "actor@example.test", "Actor", role, id, null);
    }
}
