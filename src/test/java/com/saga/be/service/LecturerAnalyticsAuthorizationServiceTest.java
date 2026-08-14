package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.RoleInTeam;
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

    @Test
    void memberMayReadOwnProgressButNotATeammate() {
        UUID courseId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID teammateId = UUID.randomUUID();
        Course course = id(new Course(), courseId);
        Team team = id(new Team(), teamId);
        team.setCourse(course);
        TeamMember member = membership(team, memberId, RoleInTeam.MEMBER);
        TeamMember teammate = membership(team, teammateId, RoleInTeam.MEMBER);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(teamMemberRepository.findByStudentIdAndTeamCourseId(memberId, courseId)).thenReturn(java.util.List.of(member));
        when(teamMemberRepository.findByStudentIdAndTeamCourseId(teammateId, courseId)).thenReturn(java.util.List.of(teammate));

        assertSame(member, service.requireStudentProgressAccess(principal(ApplicationRole.STUDENT, memberId), courseId, memberId));
        assertThrows(AccessDeniedException.class,
                () -> service.requireStudentProgressAccess(principal(ApplicationRole.STUDENT, memberId), courseId, teammateId));
    }

    @Test
    void leaderMayReadSameTeamProgressButNotAnotherTeamOrMentor() {
        UUID courseId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID otherTeamId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID otherTeamMemberId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        Course course = id(new Course(), courseId);
        Team team = id(new Team(), teamId);
        team.setCourse(course);
        Team otherTeam = id(new Team(), otherTeamId);
        otherTeam.setCourse(course);
        TeamMember leader = membership(team, leaderId, RoleInTeam.LEADER);
        TeamMember member = membership(team, memberId, RoleInTeam.MEMBER);
        TeamMember otherTeamMember = membership(otherTeam, otherTeamMemberId, RoleInTeam.MEMBER);
        TeamMember mentor = membership(team, mentorId, RoleInTeam.MENTOR);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(teamMemberRepository.findByStudentIdAndTeamCourseId(leaderId, courseId)).thenReturn(java.util.List.of(leader));
        when(teamMemberRepository.findByStudentIdAndTeamCourseId(memberId, courseId)).thenReturn(java.util.List.of(member));
        when(teamMemberRepository.findByStudentIdAndTeamCourseId(otherTeamMemberId, courseId))
                .thenReturn(java.util.List.of(otherTeamMember));
        when(teamMemberRepository.findByStudentIdAndTeamCourseId(mentorId, courseId)).thenReturn(java.util.List.of(mentor));

        assertSame(leader, service.requireStudentProgressAccess(principal(ApplicationRole.STUDENT, leaderId), courseId, leaderId));
        assertSame(member, service.requireStudentProgressAccess(principal(ApplicationRole.STUDENT, leaderId), courseId, memberId));
        assertThrows(AccessDeniedException.class,
                () -> service.requireStudentProgressAccess(principal(ApplicationRole.STUDENT, leaderId), courseId, otherTeamMemberId));
        assertThrows(AccessDeniedException.class,
                () -> service.requireStudentProgressAccess(principal(ApplicationRole.STUDENT, mentorId), courseId, memberId));
    }

    @Test
    void graphReadAllowsAdminOwningLecturerLeaderAndMemberForTheExactTeam() {
        UUID courseId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Lecturer lecturer = id(new Lecturer(), lecturerId);
        Course course = id(new Course(), courseId);
        course.setInstructor(lecturer);
        Team team = id(new Team(), teamId);
        team.setCourse(course);
        TeamMember leader = new TeamMember();
        leader.setRoleInTeam(RoleInTeam.LEADER);
        TeamMember member = new TeamMember();
        member.setRoleInTeam(RoleInTeam.MEMBER);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamIdAndStudentId(teamId, leaderId)).thenReturn(Optional.of(leader));
        when(teamMemberRepository.findByTeamIdAndStudentId(teamId, memberId)).thenReturn(Optional.of(member));

        assertSame(team, service.requireGraphReadAccess(principal(ApplicationRole.ADMIN, UUID.randomUUID()), courseId, teamId));
        assertSame(team, service.requireGraphReadAccess(principal(ApplicationRole.LECTURER, lecturerId), courseId, teamId));
        assertSame(team, service.requireGraphReadAccess(principal(ApplicationRole.STUDENT, leaderId), courseId, teamId));
        assertSame(team, service.requireGraphReadAccess(principal(ApplicationRole.STUDENT, memberId), courseId, teamId));
    }

    @Test
    void graphReadRejectsMentorAndStudentWithoutExactTeamMembership() {
        UUID courseId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID otherStudentId = UUID.randomUUID();
        Course course = id(new Course(), courseId);
        Team team = id(new Team(), teamId);
        team.setCourse(course);
        TeamMember mentor = new TeamMember();
        mentor.setRoleInTeam(RoleInTeam.MENTOR);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamIdAndStudentId(teamId, mentorId)).thenReturn(Optional.of(mentor));
        when(teamMemberRepository.findByTeamIdAndStudentId(teamId, otherStudentId)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> service.requireGraphReadAccess(principal(ApplicationRole.STUDENT, mentorId), courseId, teamId));
        assertThrows(AccessDeniedException.class,
                () -> service.requireGraphReadAccess(principal(ApplicationRole.STUDENT, otherStudentId), courseId, teamId));
    }

    @Test
    void graphReadRejectsCourseAndTeamIdMixAndMatchAsNotFound() {
        UUID requestedCourseId = UUID.randomUUID();
        UUID actualCourseId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Course requestedCourse = id(new Course(), requestedCourseId);
        Course actualCourse = id(new Course(), actualCourseId);
        Team team = id(new Team(), teamId);
        team.setCourse(actualCourse);

        when(courseRepository.findById(requestedCourseId)).thenReturn(Optional.of(requestedCourse));
        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(Optional.of(team));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.requireGraphReadAccess(
                        principal(ApplicationRole.ADMIN, UUID.randomUUID()), requestedCourseId, teamId));
        org.junit.jupiter.api.Assertions.assertEquals(404, exception.getStatusCode().value());
    }

    private TeamMember membership(Team team, UUID studentId, RoleInTeam role) {
        Student student = id(new Student(), studentId);
        TeamMember membership = new TeamMember();
        membership.setTeam(team);
        membership.setStudent(student);
        membership.setRoleInTeam(role);
        return membership;
    }

    private <T> T id(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private SagaPrincipal principal(ApplicationRole role, UUID id) {
        return new SagaPrincipal("subject", "actor@example.test", "Actor", role, id, null);
    }
}
