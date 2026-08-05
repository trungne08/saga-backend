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
import com.saga.be.entity.Subject;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.service.contribution.ContributionSliceWeights;
import com.saga.be.repository.ClassRepository;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.SemesterRepository;
import com.saga.be.repository.SubjectRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.security.SagaPrincipal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

        Subject subject = subjectRepository.findByIdAndDeletedAtIsNull(request.getSubjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subject not found"));
        Class clazz = classRepository.findById(request.getClassId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found"));
        Semester semester = semesterRepository.findById(request.getSemesterId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Semester not found"));
        Lecturer instructor = lecturerRepository.findById(request.getInstructorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lecturer not found"));
        ContributionSliceWeights defaultWeights = ContributionSliceWeights.fromCourse(null);

        Course course = Course.builder()
                .courseCode(courseCode)
                .name(request.getName().trim())
                .subject(subject)
                .clazz(clazz)
                .semester(semester)
                .instructor(instructor)
                .codeContributionWeight(defaultWeights.codeValue())
                .documentContributionWeight(defaultWeights.documentValue())
                .designContributionWeight(defaultWeights.designValue())
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
    public Page<LecturerOptionResponse> getLecturersForCourseAssignment(
            String keyword,
            String sortBy,
            String sortDirection,
            Pageable pageable
    ) {
        Specification<Lecturer> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (keyword != null && !keyword.isBlank()) {
            String needle = "%" + keyword.trim().toLowerCase() + "%";
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("fullName")), needle),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), needle)
            ));
        }

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                lecturerSort(sortBy, sortDirection)
        );
        return lecturerRepository.findAll(specification, sortedPageable)
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

        List<TeamMember> teamMembers = teamMemberRepository.findByTeamCourseId(courseId);
        List<CourseStudentRosterItem> studentsWithTeam = teamMembers.stream()
                .filter(membership -> matchesKeyword(membership.getStudent(), keyword))
                .map(membership -> rosterItem(membership, teamMembers))
                .sorted(rosterComparator(sortBy, sortDirection))
                .toList();

        Page<CourseStudentRosterItem> withTeamPage = page(studentsWithTeam, pageable);
        Page<CourseStudentRosterItem> withoutTeamPage = Page.empty(pageable);
        RosterTeamFilter teamFilter = RosterTeamFilter.from(hasTeam);

        if (teamFilter == RosterTeamFilter.WITH) {
            return new CourseStudentRosterResponse(
                    withTeamPage,
                    withoutTeamPage
            );
        }
        if (teamFilter == RosterTeamFilter.WITHOUT) {
            return new CourseStudentRosterResponse(
                    Page.empty(pageable),
                    withoutTeamPage
            );
        }

        return new CourseStudentRosterResponse(
                withTeamPage,
                withoutTeamPage
        );
    }

    private CourseStudentRosterItem rosterItem(TeamMember membership, List<TeamMember> courseMemberships) {
        Student student = membership.getStudent();
        Team team = membership.getTeam();
        List<TeamMemberResponse> teamMembersInTeam = courseMemberships.stream()
                .filter(candidate -> candidate.getTeam().getId().equals(team.getId()))
                .map(TeamMemberResponse::from)
                .toList();
        CourseStudentTeamSummaryResponse teamSummary = new CourseStudentTeamSummaryResponse(
                team.getId(),
                team.getName(),
                team.getProject() != null ? team.getProject().getId() : null,
                team.getProject() != null ? team.getProject().getName() : null,
                teamMembersInTeam
        );
        return new CourseStudentRosterItem(
                student.getId(),
                student.getFullName(),
                student.getStudentCode(),
                student.getEmail(),
                teamSummary
        );
    }

    private boolean matchesKeyword(Student student, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        return (student.getFullName() != null && student.getFullName().toLowerCase(Locale.ROOT).contains(needle))
                || (student.getStudentCode() != null && student.getStudentCode().toLowerCase(Locale.ROOT).contains(needle))
                || (student.getEmail() != null && student.getEmail().toLowerCase(Locale.ROOT).contains(needle));
    }

    private Comparator<CourseStudentRosterItem> rosterComparator(String sortBy, String sortDirection) {
        Comparator<CourseStudentRosterItem> comparator = switch (RosterSortField.from(sortBy)) {
            case FULL_NAME -> Comparator.comparing(
                    CourseStudentRosterItem::fullName,
                    Comparator.nullsLast(String::compareToIgnoreCase)
            );
            case EMAIL -> Comparator.comparing(
                    CourseStudentRosterItem::email,
                    Comparator.nullsLast(String::compareToIgnoreCase)
            );
            case TEAM_NAME -> Comparator.comparing(
                    item -> item.team() == null ? "" : item.team().teamName(),
                    Comparator.nullsLast(String::compareToIgnoreCase)
            );
            case PROJECT_NAME -> Comparator.comparing(
                    item -> item.team() == null ? "" : item.team().projectName(),
                    Comparator.nullsLast(String::compareToIgnoreCase)
            );
            case STUDENT_CODE -> Comparator.comparing(
                    CourseStudentRosterItem::studentCode,
                    Comparator.nullsLast(String::compareToIgnoreCase)
            );
        };

        if (SortDirection.from(sortDirection) == SortDirection.DESC) {
            comparator = comparator.reversed();
        }
        return comparator
                .thenComparing(CourseStudentRosterItem::studentId, Comparator.nullsLast(UUID::compareTo))
                .thenComparing(item -> item.team() == null ? null : item.team().teamId(), Comparator.nullsLast(UUID::compareTo));
    }

    private Page<CourseStudentRosterItem> page(List<CourseStudentRosterItem> items, Pageable pageable) {
        long offset = pageable.getOffset();
        if (offset >= items.size()) {
            return new PageImpl<>(List.of(), pageable, items.size());
        }
        int fromIndex = Math.toIntExact(offset);
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), items.size());
        return new PageImpl<>(items.subList(fromIndex, toIndex), pageable, items.size());
    }

    private Sort lecturerSort(String sortBy, String sortDirection) {
        LecturerSortField field = LecturerSortField.from(sortBy);
        Sort.Direction direction = SortDirection.from(sortDirection) == SortDirection.DESC
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, field.property()).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private enum RosterTeamFilter {
        ALL, WITH, WITHOUT;

        static RosterTeamFilter from(String value) {
            try {
                return valueOf(normalize(value));
            } catch (IllegalArgumentException exception) {
                throw badRequest("hasTeam must be one of: all, with, without");
            }
        }
    }

    private enum RosterSortField {
        STUDENT_CODE("studentCode"), FULL_NAME("fullName"), EMAIL("email"), TEAM_NAME("teamName"), PROJECT_NAME("projectName");

        private final String value;

        RosterSortField(String value) {
            this.value = value;
        }

        static RosterSortField from(String value) {
            for (RosterSortField field : values()) {
                if (field.value.equals(value)) {
                    return field;
                }
            }
            throw badRequest("sortBy must be one of: studentCode, fullName, email, teamName, projectName");
        }
    }

    private enum LecturerSortField {
        FULL_NAME("fullName"), EMAIL("email");

        private final String property;

        LecturerSortField(String property) {
            this.property = property;
        }

        String property() {
            return property;
        }

        static LecturerSortField from(String value) {
            for (LecturerSortField field : values()) {
                if (field.property.equals(value)) {
                    return field;
                }
            }
            throw badRequest("sortBy must be one of: fullName, email");
        }
    }

    private enum SortDirection {
        ASC, DESC;

        static SortDirection from(String value) {
            try {
                return valueOf(normalize(value));
            } catch (IllegalArgumentException exception) {
                throw badRequest("sortDirection must be one of: asc, desc");
            }
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
