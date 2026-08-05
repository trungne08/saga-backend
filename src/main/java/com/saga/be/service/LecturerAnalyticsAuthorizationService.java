package com.saga.be.service;

import com.saga.be.entity.Course;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
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
    public TeamMember requireStudentInCourse(SagaPrincipal principal, UUID courseId, UUID studentId) {
        requireCourseAccess(principal, courseId);
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
