package com.saga.be.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.saga.be.service.ClassService;
import com.saga.be.service.CourseService;
import com.saga.be.service.SemesterService;
import com.saga.be.service.SubjectService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class LocalDemoDataSeederTest {

    private static final String LEADER_SUB = "local-real-cognito-sub";

    private StudentRepository studentRepository;
    private SemesterRepository semesterRepository;
    private SubjectRepository subjectRepository;
    private ClassRepository classRepository;
    private CourseRepository courseRepository;
    private LecturerRepository lecturerRepository;
    private TeamRepository teamRepository;
    private TeamMemberRepository teamMemberRepository;
    private SemesterService semesterService;
    private SubjectService subjectService;
    private ClassService classService;
    private CourseService courseService;
    private TeamProjectService teamProjectService;

    @BeforeEach
    void setUp() {
        studentRepository = mock(StudentRepository.class);
        semesterRepository = mock(SemesterRepository.class);
        subjectRepository = mock(SubjectRepository.class);
        classRepository = mock(ClassRepository.class);
        courseRepository = mock(CourseRepository.class);
        lecturerRepository = mock(LecturerRepository.class);
        teamRepository = mock(TeamRepository.class);
        teamMemberRepository = mock(TeamMemberRepository.class);
        semesterService = mock(SemesterService.class);
        subjectService = mock(SubjectService.class);
        classService = mock(ClassService.class);
        courseService = mock(CourseService.class);
        teamProjectService = mock(TeamProjectService.class);
    }

    @Test
    void isNotRegisteredOutsideLocalOrWhenThePropertyIsFalse() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(SeederConfiguration.class);

        runner.withPropertyValues("app.local-demo-seed.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(LocalDemoDataSeeder.class));
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .withPropertyValues("app.local-demo-seed.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(LocalDemoDataSeeder.class));
    }

    @Test
    void missingLeaderStopsBeforeCreatingAnyDemoData() {
        when(studentRepository.findByCognitoSub(LEADER_SUB)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> seeder().seed());

        verify(semesterService, never()).createSemester(any());
        verify(subjectService, never()).createSubject(any());
        verify(classService, never()).createClass(any());
        verify(teamRepository, never()).save(any());
        verify(teamMemberRepository, never()).save(any());
        verify(teamProjectService, never()).create(any(), any(), any(), any());
    }

    @Test
    void createsDemoLeaderTeamAndProjectThenReusesThemOnSecondRun() {
        Student leader = entity(new Student());
        leader.setCognitoSub(LEADER_SUB);
        leader.setEmail("leader@saga.test");
        Semester semester = entity(new Semester());
        Subject subject = entity(new Subject());
        com.saga.be.entity.Class clazz = entity(new com.saga.be.entity.Class());
        Lecturer instructor = entity(new Lecturer());
        Course course = entity(new Course());
        Team team = entity(new Team());
        team.setCourse(course);
        AtomicReference<TeamMember> storedMembership = new AtomicReference<>();
        UUID projectId = UUID.randomUUID();

        when(studentRepository.findByCognitoSub(LEADER_SUB)).thenReturn(Optional.of(leader));
        when(semesterRepository.findByCode(LocalDemoDataSeeder.SEMESTER_CODE))
                .thenReturn(Optional.empty(), Optional.of(semester));
        when(subjectRepository.findBySubjectCodeAndDeletedAtIsNull(LocalDemoDataSeeder.SUBJECT_CODE))
                .thenReturn(Optional.empty(), Optional.of(subject));
        when(classRepository.findByClassCode(LocalDemoDataSeeder.CLASS_CODE))
                .thenReturn(Optional.empty(), Optional.of(clazz));
        when(semesterService.createSemester(any())).thenReturn(semester);
        when(subjectService.createSubject(any())).thenReturn(subject);
        when(classService.createClass(any())).thenReturn(clazz);
        when(lecturerRepository.findByEmailIgnoreCase("local-demo-instructor@saga.invalid"))
                .thenReturn(Optional.empty(), Optional.of(instructor));
        when(lecturerRepository.save(any(Lecturer.class))).thenReturn(instructor);
        when(courseRepository.findByCourseCode(LocalDemoDataSeeder.COURSE_CODE))
                .thenReturn(Optional.empty(), Optional.of(course));
        when(courseService.createCourse(any())).thenReturn(course);
        when(teamRepository.findByCourseIdAndName(course.getId(), LocalDemoDataSeeder.TEAM_NAME))
                .thenReturn(Optional.empty(), Optional.of(team));
        when(teamRepository.save(any(Team.class))).thenReturn(team);
        when(teamMemberRepository.findByStudentIdAndTeamCourseId(leader.getId(), course.getId()))
                .thenAnswer(invocation -> storedMembership.get() == null
                        ? List.of()
                        : List.of(storedMembership.get()));
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(invocation -> {
            TeamMember saved = invocation.getArgument(0);
            storedMembership.set(saved);
            return saved;
        });
        when(teamProjectService.create(any(), any(), any(), any())).thenAnswer(invocation -> {
            Project project = entity(new Project());
            project.setId(projectId);
            project.setName(LocalDemoDataSeeder.PROJECT_NAME);
            project.setCreatedByCognitoSub(LEADER_SUB);
            team.setProject(project);
            return new ProjectResponse(projectId, team.getId(), LocalDemoDataSeeder.PROJECT_NAME);
        });

        LocalDemoDataSeeder seeder = seeder();
        seeder.seed();

        assertThat(storedMembership.get()).isNotNull();
        assertThat(storedMembership.get().getRoleInTeam()).isEqualTo(RoleInTeam.LEADER);
        assertThat(team.getProject()).isNotNull();
        assertThat(team.getProject().getId()).isEqualTo(projectId);
        assertThat(team.getProject().getName()).isEqualTo(LocalDemoDataSeeder.PROJECT_NAME);
        verify(teamProjectService).create(any(), any(), any(), any());

        seeder.seed();

        verify(semesterService).createSemester(any());
        verify(subjectService).createSubject(any());
        verify(classService).createClass(any());
        verify(courseService).createCourse(any());
        verify(teamRepository).save(any(Team.class));
        verify(teamMemberRepository).save(any(TeamMember.class));
        verify(teamProjectService).create(any(), any(), any(), any());
    }

    private LocalDemoDataSeeder seeder() {
        return new LocalDemoDataSeeder(
                LEADER_SUB,
                studentRepository,
                semesterRepository,
                subjectRepository,
                classRepository,
                courseRepository,
                lecturerRepository,
                teamRepository,
                teamMemberRepository,
                semesterService,
                subjectService,
                classService,
                courseService,
                teamProjectService
        );
    }

    private <T extends com.saga.be.entity.BaseEntity> T entity(T entity) {
        entity.setId(UUID.randomUUID());
        return entity;
    }

    @Configuration(proxyBeanMethods = false)
    @Import(LocalDemoDataSeeder.class)
    static class SeederConfiguration {
    }
}
