package com.saga.be.service;

import com.saga.be.dto.request.CourseRequest;
import com.saga.be.dto.response.CourseStudentRosterItem;
import com.saga.be.dto.response.CourseStudentRosterResponse;
import com.saga.be.dto.response.CourseStudentTeamSummaryResponse;
import com.saga.be.dto.response.LecturerOptionResponse;
import com.saga.be.dto.response.TeamMemberResponse;
import com.saga.be.entity.Class;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Semester;
import com.saga.be.entity.Student;
import com.saga.be.entity.StudentCourseInvitation;
import com.saga.be.entity.Subject;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.repository.ClassRepository;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.SemesterRepository;
import com.saga.be.repository.StudentCourseInvitationRepository;
import com.saga.be.repository.SubjectRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.security.SagaPrincipal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final SubjectRepository subjectRepository;
    private final ClassRepository classRepository;
    private final SemesterRepository semesterRepository;
    private final LecturerRepository lecturerRepository;
    private final StudentCourseInvitationRepository invitationRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CourseImportAuthorizationService courseImportAuthorizationService;

    public Course getCourseById(UUID id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
    }

    @Transactional
    public Course createCourse(CourseRequest request) {
        String courseCode = request.getCourseCode().trim();
        if (courseRepository.existsByCourseCode(courseCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Course code already exists");
        }

        Subject subject = subjectRepository.findById(request.getSubjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subject not found"));
        Class clazz = classRepository.findById(request.getClassId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found"));
        Semester semester = semesterRepository.findById(request.getSemesterId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Semester not found"));
        Lecturer instructor = lecturerRepository.findById(request.getInstructorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lecturer not found"));

        Course course = Course.builder()
                .courseCode(courseCode)
                .name(request.getName().trim())
                .subject(subject)
                .clazz(clazz)
                .semester(semester)
                .instructor(instructor)
                .build();

        return courseRepository.save(course);
    }

    public Page<Course> getCoursesWithFilters(UUID subjectId, UUID semesterId, UUID instructorId, Pageable pageable) {
        Specification<Course> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (subjectId != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("subject").get("id"), subjectId));
        }
        if (semesterId != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("semester").get("id"), semesterId));
        }
        if (instructorId != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("instructor").get("id"), instructorId));
        }

        return courseRepository.findAll(specification, pageable);
    }

    @Transactional(readOnly = true)
    public Page<LecturerOptionResponse> getLecturersForCourseAssignment(String keyword, Pageable pageable) {
        Specification<Lecturer> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (keyword != null && !keyword.isBlank()) {
            String needle = "%" + keyword.trim().toLowerCase() + "%";
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("fullName")), needle),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), needle),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("cognitoSub")), needle)
            ));
        }

        return lecturerRepository.findAll(specification, pageable)
                .map(LecturerOptionResponse::from);
    }

    @Transactional(readOnly = true)
    public CourseStudentRosterResponse getCourseRoster(
            SagaPrincipal principal,
            UUID courseId,
            String keyword,
            String hasTeam,
            String sortBy,
            String sortDirection,
            Pageable pageable
    ) {
        courseImportAuthorizationService.requireImportAccess(principal, courseId);

        Page<StudentCourseInvitation> invitationPage = invitationRepository.findByCourseId(courseId, pageable);
        List<TeamMember> teamMembers = teamMemberRepository.findByTeamCourseId(courseId);

        Map<UUID, TeamMember> teamMembershipByStudentId = teamMembers.stream()
                .collect(Collectors.toMap(
                        membership -> membership.getStudent().getId(),
                        Function.identity(),
                        (left, right) -> left
                ));
        Map<UUID, List<TeamMember>> teamMembersByTeamId = teamMembers.stream()
                .collect(Collectors.groupingBy(membership -> membership.getTeam().getId()));

        List<CourseStudentRosterItem> studentsWithTeam = new ArrayList<>();
        List<CourseStudentRosterItem> studentsWithoutTeam = new ArrayList<>();

        for (StudentCourseInvitation invitation : invitationPage.getContent()) {
            Student student = invitation.getStudent();
            if (!matchesKeyword(student, keyword)) {
                continue;
            }

            TeamMember teamMembership = teamMembershipByStudentId.get(student.getId());

            if (teamMembership == null || teamMembership.getTeam() == null) {
                studentsWithoutTeam.add(new CourseStudentRosterItem(
                        student.getId(),
                        student.getFullName(),
                        student.getStudentCode(),
                        student.getEmail(),
                        null
                ));
                continue;
            }

            Team team = teamMembership.getTeam();
            List<TeamMemberResponse> teamMembersInTeam = teamMembersByTeamId.getOrDefault(team.getId(), List.of())
                    .stream()
                    .map(TeamMemberResponse::from)
                    .toList();

            CourseStudentTeamSummaryResponse teamSummary = new CourseStudentTeamSummaryResponse(
                    team.getId(),
                    team.getName(),
                    team.getProject() != null ? team.getProject().getId() : null,
                    team.getProject() != null ? team.getProject().getName() : null,
                    teamMembersInTeam
            );

            studentsWithTeam.add(new CourseStudentRosterItem(
                    student.getId(),
                    student.getFullName(),
                    student.getStudentCode(),
                    student.getEmail(),
                    teamSummary
            ));
        }

        sortItems(studentsWithTeam, sortBy, sortDirection);
        sortItems(studentsWithoutTeam, sortBy, sortDirection);

        if ("with".equalsIgnoreCase(hasTeam)) {
            return new CourseStudentRosterResponse(
                    new PageImpl<>(studentsWithTeam, pageable, studentsWithTeam.size()),
                    Page.empty(pageable)
            );
        }
        if ("without".equalsIgnoreCase(hasTeam)) {
            return new CourseStudentRosterResponse(
                    Page.empty(pageable),
                    new PageImpl<>(studentsWithoutTeam, pageable, studentsWithoutTeam.size())
            );
        }

        return new CourseStudentRosterResponse(
                new PageImpl<>(studentsWithTeam, pageable, studentsWithTeam.size()),
                new PageImpl<>(studentsWithoutTeam, pageable, studentsWithoutTeam.size())
        );
    }

    private boolean matchesKeyword(Student student, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String needle = keyword.trim().toLowerCase();
        return (student.getFullName() != null && student.getFullName().toLowerCase().contains(needle))
                || (student.getStudentCode() != null && student.getStudentCode().toLowerCase().contains(needle))
                || (student.getEmail() != null && student.getEmail().toLowerCase().contains(needle));
    }

    private void sortItems(List<CourseStudentRosterItem> students, String sortBy, String sortDirection) {
        Comparator<CourseStudentRosterItem> comparator;
        switch (sortBy) {
            case "fullName" -> comparator = Comparator.comparing(
                    CourseStudentRosterItem::fullName,
                    Comparator.nullsLast(String::compareToIgnoreCase)
            );
            case "email" -> comparator = Comparator.comparing(
                    CourseStudentRosterItem::email,
                    Comparator.nullsLast(String::compareToIgnoreCase)
            );
            case "teamName" -> comparator = Comparator.comparing(
                    item -> item.team() == null ? "" : item.team().teamName(),
                    Comparator.nullsLast(String::compareToIgnoreCase)
            );
            case "projectName" -> comparator = Comparator.comparing(
                    item -> item.team() == null ? "" : item.team().projectName(),
                    Comparator.nullsLast(String::compareToIgnoreCase)
            );
            case "studentCode" -> comparator = Comparator.comparing(
                    CourseStudentRosterItem::studentCode,
                    Comparator.nullsLast(String::compareToIgnoreCase)
            );
            default -> comparator = Comparator.comparing(
                    CourseStudentRosterItem::studentCode,
                    Comparator.nullsLast(String::compareToIgnoreCase)
            );
        }

        if ("desc".equalsIgnoreCase(sortDirection)) {
            comparator = comparator.reversed();
        }
        students.sort(comparator);
    }
}
