package com.saga.be.config;

import com.saga.be.dto.request.ClassRequest;
import com.saga.be.dto.request.CourseRequest;
import com.saga.be.dto.request.CreateTeamProjectRequest;
import com.saga.be.dto.request.SemesterRequest;
import com.saga.be.dto.request.SubjectRequest;
import com.saga.be.dto.response.ProjectResponse;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Project;
import com.saga.be.entity.Semester;
import com.saga.be.entity.Student;
import com.saga.be.entity.Subject;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.integration.project.TeamProjectService;
import com.saga.be.repository.ClassRepository;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.SemesterRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.SubjectRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.ClassService;
import com.saga.be.service.CourseService;
import com.saga.be.service.SemesterService;
import com.saga.be.service.SubjectService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates only explicitly named local demo records for an existing SAGA Student. */
@Component
@Profile("local")
@ConditionalOnProperty(name = "app.local-demo-seed.enabled", havingValue = "true")
@Slf4j
public class LocalDemoDataSeeder implements ApplicationRunner {

    static final String SEMESTER_CODE = "SAGA-LOCAL-DEMO-2026";
    static final String SUBJECT_CODE = "SAGA-LOCAL-DEMO";
    static final String CLASS_CODE = "SAGA-LOCAL-DEMO";
    static final String COURSE_CODE = "SAGA-LOCAL-DEMO";
    static final String TEAM_NAME = "SAGA Local Demo Team";
    static final String PROJECT_NAME = "SAGA Local Demo Project";
    private static final String DEMO_INSTRUCTOR_EMAIL = "local-demo-instructor@saga.invalid";

    private final String leaderCognitoSub;
    private final StudentRepository studentRepository;
    private final SemesterRepository semesterRepository;
    private final SubjectRepository subjectRepository;
    private final ClassRepository classRepository;
    private final CourseRepository courseRepository;
    private final LecturerRepository lecturerRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final SemesterService semesterService;
    private final SubjectService subjectService;
    private final ClassService classService;
    private final CourseService courseService;
    private final TeamProjectService teamProjectService;

    public LocalDemoDataSeeder(
            @Value("${app.local-demo-seed.leader-cognito-sub:}") String leaderCognitoSub,
            StudentRepository studentRepository,
            SemesterRepository semesterRepository,
            SubjectRepository subjectRepository,
            ClassRepository classRepository,
            CourseRepository courseRepository,
            LecturerRepository lecturerRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            SemesterService semesterService,
            SubjectService subjectService,
            ClassService classService,
            CourseService courseService,
            TeamProjectService teamProjectService
    ) {
        this.leaderCognitoSub = leaderCognitoSub;
        this.studentRepository = studentRepository;
        this.semesterRepository = semesterRepository;
        this.subjectRepository = subjectRepository;
        this.classRepository = classRepository;
        this.courseRepository = courseRepository;
        this.lecturerRepository = lecturerRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.semesterService = semesterService;
        this.subjectService = subjectService;
        this.classService = classService;
        this.courseService = courseService;
        this.teamProjectService = teamProjectService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed();
    }

    public void seed() {
        if (leaderCognitoSub == null || leaderCognitoSub.isBlank()) {
            throw new IllegalStateException("LOCAL_DEMO_LEADER_COGNITO_SUB is required for local demo seed");
        }
        Student leader = studentRepository.findByCognitoSub(leaderCognitoSub)
                .orElseThrow(() -> new IllegalStateException(
                        "No existing Student profile matches the configured local demo leader"));

        Semester semester = semesterRepository.findByCode(SEMESTER_CODE)
                .orElseGet(this::createSemester);
        Subject subject = subjectRepository.findBySubjectCodeAndDeletedAtIsNull(SUBJECT_CODE)
                .orElseGet(this::createSubject);
        com.saga.be.entity.Class clazz = classRepository.findByClassCode(CLASS_CODE)
                .orElseGet(this::createClass);
        Lecturer instructor = lecturerRepository.findByEmailIgnoreCase(DEMO_INSTRUCTOR_EMAIL)
                .orElseGet(() -> lecturerRepository.save(Lecturer.builder()
                        .email(DEMO_INSTRUCTOR_EMAIL)
                        .fullName("SAGA Local Demo Instructor")
                        .build()));
        Course course = courseRepository.findByCourseCode(COURSE_CODE)
                .orElseGet(() -> createCourse(subject, clazz, semester, instructor));
        Team team = teamRepository.findByCourseIdAndName(course.getId(), TEAM_NAME)
                .orElseGet(() -> teamRepository.save(Team.builder().course(course).name(TEAM_NAME).build()));

        List<TeamMember> courseMemberships = teamMemberRepository
                .findByStudentIdAndTeamCourseId(leader.getId(), course.getId());
        if (courseMemberships.size() > 1) {
            throw new IllegalStateException("Local demo leader has invalid multiple Team memberships in the demo Course");
        }
        TeamMember membership = courseMemberships.isEmpty()
                ? TeamMember.builder().team(team).student(leader).build()
                : courseMemberships.get(0);
        if (!membership.getTeam().getId().equals(team.getId())) {
            throw new IllegalStateException("Local demo leader already belongs to another Team in the demo Course");
        }
        if (membership.getRoleInTeam() != RoleInTeam.LEADER) {
            membership.setRoleInTeam(RoleInTeam.LEADER);
            teamMemberRepository.save(membership);
        }

        Project project = ensureProject(team, leader);
        log.info("Local demo seed ready: teamId={}, projectId={}", team.getId(), project.getId());
    }

    private Semester createSemester() {
        SemesterRequest request = new SemesterRequest();
        request.setCode(SEMESTER_CODE);
        request.setName("SAGA Local Demo Semester");
        request.setStartDate(LocalDateTime.of(2026, 1, 1, 0, 0));
        request.setEndDate(LocalDateTime.of(2026, 12, 31, 23, 59));
        return semesterService.createSemester(request);
    }

    private Subject createSubject() {
        SubjectRequest request = new SubjectRequest();
        request.setSubjectCode(SUBJECT_CODE);
        request.setName("SAGA Local Demo Subject");
        return subjectService.createSubject(request);
    }

    private com.saga.be.entity.Class createClass() {
        ClassRequest request = new ClassRequest();
        request.setClassCode(CLASS_CODE);
        request.setName("SAGA Local Demo Class");
        return classService.createClass(request);
    }

    private Course createCourse(
            Subject subject,
            com.saga.be.entity.Class clazz,
            Semester semester,
            Lecturer instructor
    ) {
        CourseRequest request = new CourseRequest();
        request.setCourseCode(COURSE_CODE);
        request.setName("SAGA Local Demo Course");
        request.setSubjectId(subject.getId());
        request.setClassId(clazz.getId());
        request.setSemesterId(semester.getId());
        request.setInstructorId(instructor.getId());
        return courseService.createCourse(request);
    }

    private Project ensureProject(Team team, Student leader) {
        Project existing = team.getProject();
        if (existing != null) {
            if (PROJECT_NAME.equals(existing.getName())
                    && leaderCognitoSub.equals(existing.getCreatedByCognitoSub())) {
                return existing;
            }
            throw new IllegalStateException("The local demo team is linked to a non-demo project");
        }
        SagaPrincipal principal = new SagaPrincipal(
                leaderCognitoSub,
                leader.getEmail(),
                leader.getFullName(),
                ApplicationRole.STUDENT,
                leader.getId(),
                leader.getAccountStatus()
        );
        ProjectResponse response = teamProjectService.create(
                principal,
                team.getId(),
                new CreateTeamProjectRequest(PROJECT_NAME),
                "local-demo-seed"
        );
        Project project = team.getProject();
        if (project == null || !response.id().equals(project.getId())) {
            throw new IllegalStateException("Local demo project was not linked to its team");
        }
        return project;
    }
}
