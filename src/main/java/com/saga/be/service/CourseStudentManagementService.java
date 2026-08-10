package com.saga.be.service;

import com.saga.be.dto.request.CourseManualStudentRequest;
import com.saga.be.dto.request.CourseStudentTeamUpdateRequest;
import com.saga.be.dto.response.CourseStudentMutationResponse;
import com.saga.be.entity.Course;
import com.saga.be.entity.Student;
import com.saga.be.entity.StudentCourseInvitation;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.exception.IdentityConflictException;
import com.saga.be.helper.StudentIdentityNormalizer;
import com.saga.be.repository.PeerReviewRepository;
import com.saga.be.repository.StudentCourseInvitationRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.SagaPrincipal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CourseStudentManagementService {

    private static final String TEAM_PREFIX = "Group ";

    private final CourseImportAuthorizationService authorizationService;
    private final StudentRepository studentRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final StudentCourseInvitationRepository invitationRepository;
    private final StudentInvitationOutboxService invitationOutboxService;
    private final StudentIdentityNormalizer identityNormalizer;
    private final TaskRepository taskRepository;
    private final PeerReviewRepository peerReviewRepository;

    @Transactional
    public CourseStudentMutationResponse addStudentManually(
            SagaPrincipal principal,
            UUID courseId,
            CourseManualStudentRequest request
    ) {
        Course course = authorizationService.requireImportAccess(principal, courseId);
        Student student = resolveOrCreateStudent(request);
        invitationOutboxService.enqueueForCourse(student, course);

        TeamMember membership = null;
        String group = normalizeGroup(request.group());
        if (group == null && Boolean.TRUE.equals(request.leader())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "leader=true requires group");
        }
        if (group != null) {
            membership = assignStudentToGroup(course, student, group, toRole(request.leader()), true);
        }
        return response(
                "COURSE_STUDENT_MANUAL_ADD",
                "Added student to course successfully",
                student,
                true,
                membership
        );
    }

    @Transactional
    public CourseStudentMutationResponse updateStudentInCourse(
            SagaPrincipal principal,
            UUID courseId,
            UUID studentId,
            CourseStudentTeamUpdateRequest request
    ) {
        Course course = authorizationService.requireImportAccess(principal, courseId);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
        ensureEnrolledInCourse(courseId, studentId);

        String group = normalizeGroup(request.group());
        if (group == null && Boolean.TRUE.equals(request.leader())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "leader=true requires group");
        }
        if (group == null) {
            TeamMember removed = removeMembership(courseId, studentId, true);
            return response(
                    "COURSE_STUDENT_TEAM_UPDATE",
                    removed == null
                            ? "Student is already ungrouped"
                            : "Student moved to ungrouped list",
                    student,
                    true,
                    null
            );
        }

        invitationOutboxService.enqueueForCourse(student, course);
        TeamMember membership = assignStudentToGroup(course, student, group, toRole(request.leader()), false);
        return response(
                "COURSE_STUDENT_TEAM_UPDATE",
                "Student group updated successfully",
                student,
                true,
                membership
        );
    }

    @Transactional
    public CourseStudentMutationResponse removeStudentFromCourse(
            SagaPrincipal principal,
            UUID courseId,
            UUID studentId
    ) {
        authorizationService.requireImportAccess(principal, courseId);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
        ensureEnrolledInCourse(courseId, studentId);
        ensureCanBeRemoved(courseId, studentId);

        removeMembership(courseId, studentId, false);
        invitationRepository.deleteByStudentIdAndCourseId(studentId, courseId);

        return response(
                "COURSE_STUDENT_REMOVE",
                "Removed student from course successfully",
                student,
                false,
                null
        );
    }

    private Student resolveOrCreateStudent(CourseManualStudentRequest request) {
        String normalizedCode = identityNormalizer.normalizeStudentCode(request.studentCode());
        String normalizedEmail = identityNormalizer.normalizeEmail(request.email());
        String fullName = request.fullName().trim();

        Student byEmail = studentRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        Student byCode = studentRepository.findByStudentCodeIgnoreCase(normalizedCode).orElse(null);
        if (byEmail == null && byCode == null) {
            return studentRepository.save(Student.builder()
                    .studentCode(normalizedCode)
                    .email(normalizedEmail)
                    .fullName(fullName)
                    .accountStatus(AccountStatus.PENDING)
                    .build());
        }
        if (byEmail != null && byCode != null && byEmail.getId().equals(byCode.getId())) {
            return byEmail;
        }
        throw new IdentityConflictException("The student identity conflicts with an existing local profile");
    }

    private TeamMember assignStudentToGroup(
            Course course,
            Student student,
            String group,
            RoleInTeam role,
            boolean enforceProjectConstraint
    ) {
        List<TeamMember> memberships = teamMemberRepository.findAllByStudentIdAndTeamCourseId(student.getId(), course.getId());
        if (memberships.size() > 1) {
            throw new IdentityConflictException("Student has multiple team memberships in this course");
        }
        TeamMember existingMembership = memberships.isEmpty() ? null : memberships.get(0);
        if (existingMembership != null
                && enforceProjectConstraint
                && existingMembership.getTeam() != null
                && existingMembership.getTeam().getProject() != null
                && !existingMembership.getTeam().getName().equals(teamName(group))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot move student from a team that already has a project");
        }

        Team targetTeam = teamRepository.findByCourseIdAndName(course.getId(), teamName(group))
                .orElseGet(() -> teamRepository.save(Team.builder()
                        .course(course)
                        .name(teamName(group))
                        .build()));

        if (existingMembership == null) {
            TeamMember membership = TeamMember.builder()
                    .team(targetTeam)
                    .student(student)
                    .roleInTeam(role)
                    .build();
            return teamMemberRepository.save(membership);
        }
        if (!existingMembership.getTeam().getId().equals(targetTeam.getId())) {
            Team previousTeam = existingMembership.getTeam();
            existingMembership.setTeam(targetTeam);
            existingMembership.setRoleInTeam(role);
            TeamMember saved = teamMemberRepository.save(existingMembership);
            cleanupTeamIfEmpty(previousTeam);
            return saved;
        }
        existingMembership.setRoleInTeam(role);
        return teamMemberRepository.save(existingMembership);
    }

    private TeamMember removeMembership(UUID courseId, UUID studentId, boolean enforceProjectConstraint) {
        List<TeamMember> memberships = teamMemberRepository.findAllByStudentIdAndTeamCourseId(studentId, courseId);
        if (memberships.isEmpty()) {
            return null;
        }
        if (memberships.size() > 1) {
            throw new IdentityConflictException("Student has multiple team memberships in this course");
        }
        TeamMember membership = memberships.get(0);
        if (enforceProjectConstraint && membership.getTeam() != null && membership.getTeam().getProject() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot ungroup student from a team that already has a project");
        }
        Team team = membership.getTeam();
        teamMemberRepository.delete(membership);
        cleanupTeamIfEmpty(team);
        return membership;
    }

    private void ensureCanBeRemoved(UUID courseId, UUID studentId) {
        List<TeamMember> memberships = teamMemberRepository.findAllByStudentIdAndTeamCourseId(studentId, courseId);
        for (TeamMember membership : memberships) {
            if (membership.getTeam() != null && membership.getTeam().getProject() != null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Cannot remove student from course because the student belongs to a project team"
                );
            }
        }
        if (taskRepository.existsByProjectCourseIdAndAssigneeId(courseId, studentId)
                || taskRepository.existsByProjectCourseIdAndReporterId(courseId, studentId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot remove student from course because the student is referenced by tasks"
            );
        }
        if (peerReviewRepository.existsBySprintBoardProjectCourseIdAndReviewerId(courseId, studentId)
                || peerReviewRepository.existsBySprintBoardProjectCourseIdAndRevieweeId(courseId, studentId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot remove student from course because the student is referenced by peer reviews"
            );
        }
    }

    private void ensureEnrolledInCourse(UUID courseId, UUID studentId) {
        if (invitationRepository.existsByStudentIdAndCourseId(studentId, courseId)
                || teamMemberRepository.existsByStudentIdAndTeamCourseId(studentId, courseId)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student is not enrolled in this course");
    }

    private void cleanupTeamIfEmpty(Team team) {
        if (team == null || team.getProject() != null) {
            return;
        }
        if (teamMemberRepository.countByTeamId(team.getId()) == 0) {
            teamRepository.delete(team);
        }
    }

    private String normalizeGroup(String group) {
        if (group == null || group.isBlank()) {
            return null;
        }
        return group.trim();
    }

    private String teamName(String group) {
        if (group.toLowerCase(Locale.ROOT).startsWith("group ")) {
            return "Group " + group.substring(6).trim();
        }
        return TEAM_PREFIX + group;
    }

    private RoleInTeam toRole(Boolean leader) {
        return Boolean.TRUE.equals(leader) ? RoleInTeam.LEADER : RoleInTeam.MEMBER;
    }

    private CourseStudentMutationResponse response(
            String operation,
            String message,
            Student student,
            boolean enrolledInCourse,
            TeamMember membership
    ) {
        return new CourseStudentMutationResponse(
                operation,
                message,
                student.getId(),
                student.getStudentCode(),
                student.getEmail(),
                student.getFullName(),
                enrolledInCourse,
                membership == null || membership.getTeam() == null ? null : membership.getTeam().getId(),
                membership == null || membership.getTeam() == null ? null : membership.getTeam().getName(),
                membership == null ? null : membership.getRoleInTeam()
        );
    }
}
