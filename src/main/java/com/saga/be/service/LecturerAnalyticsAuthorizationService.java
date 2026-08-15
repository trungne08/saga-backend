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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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

        List<TeamMember> actorMemberships = teamMemberRepository.findByStudentIdAndTeamCourseId(
                principal.localProfileId(), courseId);
        if (actorMemberships.isEmpty()) {
            throw new AccessDeniedException("You do not have access to this student progress");
        }

        List<TeamMember> leaderMemberships = actorMemberships.stream()
                .filter(membership -> membership.getRoleInTeam() == RoleInTeam.LEADER)
                .filter(membership -> teamId(membership) != null)
                .toList();
        if (!leaderMemberships.isEmpty()) {
            return requireLeaderExactTeamProgress(principal.localProfileId(), studentId, courseId,
                    actorMemberships, leaderMemberships);
        }

        List<TeamMember> memberMemberships = actorMemberships.stream()
                .filter(membership -> membership.getRoleInTeam() == RoleInTeam.MEMBER)
                .toList();
        if (memberMemberships.isEmpty() || !principal.localProfileId().equals(studentId)) {
            throw new AccessDeniedException("You do not have access to this student progress");
        }
        if (memberMemberships.size() != 1) {
            throw ambiguousCourseMembership();
        }
        return memberMemberships.get(0);
    }

    private TeamMember requireLeaderExactTeamProgress(
            UUID actorStudentId,
            UUID studentId,
            UUID courseId,
            List<TeamMember> actorMemberships,
            List<TeamMember> leaderMemberships
    ) {
        Set<UUID> ledTeamIds = leaderMemberships.stream()
                .map(LecturerAnalyticsAuthorizationService::teamId)
                .collect(Collectors.toSet());
        List<TeamMember> targetMemberships = actorStudentId.equals(studentId)
                ? actorMemberships
                : teamMemberRepository.findByStudentIdAndTeamCourseId(studentId, courseId);
        if (targetMemberships.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Student trong Course");
        }
        List<TeamMember> exactTeamMemberships = targetMemberships.stream()
                .filter(membership -> ledTeamIds.contains(teamId(membership)))
                .toList();
        if (exactTeamMemberships.isEmpty()) {
            throw new AccessDeniedException("You do not have access to this student progress");
        }
        if (exactTeamMemberships.size() != 1) {
            throw ambiguousCourseMembership();
        }
        return exactTeamMemberships.get(0);
    }

    private static UUID teamId(TeamMember membership) {
        return membership.getTeam() == null ? null : membership.getTeam().getId();
    }

    private static ResponseStatusException ambiguousCourseMembership() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Student có nhiều Team membership trong Course");
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
