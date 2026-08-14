package com.saga.be.service;

import com.saga.be.entity.Course;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LecturerAnalyticsAuthorizationService {

    private final CourseRepository courseRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    @Transactional(readOnly = true)
    public Course requireCourseAccess(SagaPrincipal principal, UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        if (principal != null && principal.applicationRole() == ApplicationRole.ADMIN) {
            return course;
        }
        if (principal != null && principal.applicationRole() == ApplicationRole.LECTURER
                && course.getInstructor() != null
                && Objects.equals(principal.localProfileId(), course.getInstructor().getId())) {
            return course;
        }
        throw new AccessDeniedException("Chỉ ADMIN hoặc Lecturer phụ trách Course được xem analytics");
    }

    @Transactional(readOnly = true)
    public Team requireTeam(SagaPrincipal principal, UUID courseId, UUID teamId) {
        requireCourseAccess(principal, courseId);
        Team team = teamRepository.findWithCourseAndInstructorById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Team trong Course"));
        if (team.getCourse() == null || !courseId.equals(team.getCourse().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Team trong Course");
        }
        return team;
    }

    @Transactional(readOnly = true)
    public Team requireGraphReadAccess(SagaPrincipal principal, UUID courseId, UUID teamId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        Team team = teamRepository.findWithCourseAndInstructorById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Team trong Course"));
        if (team.getCourse() == null || !courseId.equals(team.getCourse().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Team trong Course");
        }
        if (principal == null || principal.localProfileId() == null) {
            throw new AccessDeniedException("Authentication is required");
        }
        if (principal.applicationRole() == ApplicationRole.ADMIN) {
            return team;
        }
        if (principal.applicationRole() == ApplicationRole.LECTURER
                && course.getInstructor() != null
                && Objects.equals(principal.localProfileId(), course.getInstructor().getId())) {
            return team;
        }
        if (principal.applicationRole() == ApplicationRole.STUDENT) {
            TeamMember membership = teamMemberRepository.findByTeamIdAndStudentId(
                    teamId,
                    principal.localProfileId()
            ).orElse(null);
            if (membership != null
                    && (membership.getRoleInTeam() == RoleInTeam.LEADER
                    || membership.getRoleInTeam() == RoleInTeam.MEMBER)) {
                return team;
            }
        }
        throw new AccessDeniedException("You do not have access to this Team analytics graph");
    }

    @Transactional(readOnly = true)
    public TeamMember requireStudentInCourse(SagaPrincipal principal, UUID courseId, UUID studentId) {
        requireCourseAccess(principal, courseId);
        return requireUniqueCourseMembership(studentId, courseId);
    }

    @Transactional(readOnly = true)
    public TeamMember requireStudentProgressAccess(SagaPrincipal principal, UUID courseId, UUID studentId) {
        if (principal == null || principal.localProfileId() == null) {
            throw new AccessDeniedException("Authentication is required");
        }
        if (principal.applicationRole() == ApplicationRole.ADMIN
                || principal.applicationRole() == ApplicationRole.LECTURER) {
            return requireStudentInCourse(principal, courseId, studentId);
        }
        if (principal.applicationRole() != ApplicationRole.STUDENT) {
            throw new AccessDeniedException("You do not have access to this student progress");
        }
        if (courseRepository.findById(courseId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }

        TeamMember actorMembership = requireActorCourseMembership(principal.localProfileId(), courseId);
        if (actorMembership.getRoleInTeam() != RoleInTeam.LEADER
                && actorMembership.getRoleInTeam() != RoleInTeam.MEMBER) {
            throw new AccessDeniedException("You do not have access to this student progress");
        }

        TeamMember targetMembership = requireUniqueCourseMembership(studentId, courseId);
        Team actorTeam = actorMembership.getTeam();
        Team targetTeam = targetMembership.getTeam();
        if (actorTeam == null
                || targetTeam == null
                || actorTeam.getId() == null
                || !actorTeam.getId().equals(targetTeam.getId())) {
            throw new AccessDeniedException("You do not have access to this student progress");
        }
        if (actorMembership.getRoleInTeam() == RoleInTeam.MEMBER
                && !principal.localProfileId().equals(studentId)) {
            throw new AccessDeniedException("You do not have access to this student progress");
        }
        return targetMembership;
    }

    private TeamMember requireActorCourseMembership(UUID actorStudentId, UUID courseId) {
        List<TeamMember> memberships = teamMemberRepository.findByStudentIdAndTeamCourseId(actorStudentId, courseId);
        if (memberships.isEmpty()) {
            throw new AccessDeniedException("You do not have access to this student progress");
        }
        if (memberships.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Student có nhiều Team membership trong Course");
        }
        return memberships.get(0);
    }

    private TeamMember requireUniqueCourseMembership(UUID studentId, UUID courseId) {
        List<TeamMember> memberships = teamMemberRepository.findByStudentIdAndTeamCourseId(studentId, courseId);
        if (memberships.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Student trong Course");
        }
        if (memberships.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Student có nhiều Team membership trong Course");
        }
        return memberships.get(0);
    }
}
