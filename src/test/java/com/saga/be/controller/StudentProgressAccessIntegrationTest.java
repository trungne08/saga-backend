package com.saga.be.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Project;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@Transactional
class StudentProgressAccessIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private LecturerRepository lecturerRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;

    @Test
    void memberMayReadOwnProgressButNotATeammate() throws Exception {
        Fixture fixture = fixture();
        progress(fixture.course(), fixture.member(), ApplicationRole.STUDENT, fixture.member().getId())
                .andExpect(status().isOk());
        progress(fixture.course(), fixture.leader(), ApplicationRole.STUDENT, fixture.member().getId())
                .andExpect(status().isForbidden());
    }

    @Test
    void leaderMayReadSelfAndSameTeamButNotOtherTeamOrCourse() throws Exception {
        Fixture fixture = fixture();
        progress(fixture.course(), fixture.leader(), ApplicationRole.STUDENT, fixture.leader().getId())
                .andExpect(status().isOk());
        progress(fixture.course(), fixture.member(), ApplicationRole.STUDENT, fixture.leader().getId())
                .andExpect(status().isOk());
        progress(fixture.course(), fixture.otherTeamMember(), ApplicationRole.STUDENT, fixture.leader().getId())
                .andExpect(status().isForbidden());
        progress(fixture.otherCourse(), fixture.otherCourseMember(), ApplicationRole.STUDENT, fixture.leader().getId())
                .andExpect(status().isForbidden());
    }

    @Test
    void mentorIsForbiddenAndAnonymousIsUnauthorized() throws Exception {
        Fixture fixture = fixture();
        progress(fixture.course(), fixture.member(), ApplicationRole.STUDENT, fixture.mentor().getId())
                .andExpect(status().isForbidden());
        mockMvc.perform(get(progressPath(fixture.course().getId(), fixture.member().getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lecturerOwnerAndAdminRetainAccessWhileOtherLecturerIsForbidden() throws Exception {
        Fixture fixture = fixture();
        progress(fixture.course(), fixture.member(), ApplicationRole.LECTURER, fixture.owner().getId())
                .andExpect(status().isOk());
        progress(fixture.course(), fixture.member(), ApplicationRole.ADMIN, UUID.randomUUID())
                .andExpect(status().isOk());
        progress(fixture.course(), fixture.member(), ApplicationRole.LECTURER, fixture.otherLecturer().getId())
                .andExpect(status().isForbidden());
    }

    @Test
    void studentAccessIsNotOpenedToOtherLecturerAnalyticsRoutes() throws Exception {
        Fixture fixture = fixture();
        UUID courseId = fixture.course().getId();
        UUID studentId = fixture.member().getId();
        for (String path : List.of(
                "/api/v1/courses/%s/students/%s/activities".formatted(courseId, studentId),
                "/api/v1/courses/%s/students/%s/contribution-detail".formatted(courseId, studentId),
                "/api/v1/courses/%s/early-warnings".formatted(courseId),
                "/api/v1/courses/%s/dashboard/teams-progress".formatted(courseId),
                "/api/v1/courses/%s/dashboard/contribution-summary".formatted(courseId),
                "/api/v1/courses/%s/dashboard/trends".formatted(courseId),
                "/api/v1/courses/%s/dashboard/at-risk-summary".formatted(courseId)
        )) {
            mockMvc.perform(get(path).with(authentication(authenticationFor(
                    ApplicationRole.STUDENT, fixture.leader().getId()))))
                    .andExpect(status().isForbidden());
        }
    }

    private ResultActions progress(Course course, Student target, ApplicationRole role, UUID actorId)
            throws Exception {
        return mockMvc.perform(get(progressPath(course.getId(), target.getId()))
                .with(authentication(authenticationFor(role, actorId))));
    }

    private String progressPath(UUID courseId, UUID studentId) {
        return "/api/v1/courses/%s/students/%s/progress".formatted(courseId, studentId);
    }

    private Fixture fixture() {
        Lecturer owner = lecturer("owner");
        Lecturer otherLecturer = lecturer("other");
        Course course = course(owner, "primary");
        Team team = team(course, "primary");
        Team otherTeam = team(course, "same-course-other");
        Course otherCourse = course(lecturer("other-course-owner"), "other-course");
        Team otherCourseTeam = team(otherCourse, "other-course");

        Student leader = student("leader");
        Student member = student("member");
        Student mentor = student("mentor");
        Student otherTeamMember = student("other-team-member");
        Student otherCourseMember = student("other-course-member");
        membership(team, leader, RoleInTeam.LEADER);
        membership(team, member, RoleInTeam.MEMBER);
        membership(team, mentor, RoleInTeam.MENTOR);
        membership(otherTeam, otherTeamMember, RoleInTeam.MEMBER);
        membership(otherCourseTeam, otherCourseMember, RoleInTeam.MEMBER);
        return new Fixture(owner, otherLecturer, course, otherCourse, leader, member, mentor, otherTeamMember, otherCourseMember);
    }

    private Lecturer lecturer(String label) {
        String suffix = UUID.randomUUID().toString();
        return lecturerRepository.save(Lecturer.builder()
                .cognitoSub("lecturer-" + label + "-" + suffix)
                .email("lecturer-" + label + "-" + suffix + "@example.test")
                .fullName("Lecturer " + label)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private Course course(Lecturer instructor, String label) {
        return courseRepository.save(Course.builder()
                .courseCode("PROGRESS-" + label + "-" + UUID.randomUUID())
                .name("Progress " + label)
                .instructor(instructor)
                .build());
    }

    private Team team(Course course, String label) {
        Project project = projectRepository.save(Project.builder()
                .course(course)
                .name("Project " + label + " " + UUID.randomUUID())
                .build());
        return teamRepository.save(Team.builder()
                .course(course)
                .project(project)
                .name("Team " + label + " " + UUID.randomUUID())
                .build());
    }

    private Student student(String label) {
        String suffix = UUID.randomUUID().toString();
        return studentRepository.save(Student.builder()
                .cognitoSub("student-" + label + "-" + suffix)
                .email("student-" + label + "-" + suffix + "@example.test")
                .studentCode("SE" + suffix.substring(0, 6).toUpperCase())
                .fullName("Student " + label)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private void membership(Team team, Student student, RoleInTeam role) {
        teamMemberRepository.save(TeamMember.builder()
                .team(team)
                .student(student)
                .roleInTeam(role)
                .build());
    }

    private Authentication authenticationFor(ApplicationRole role, UUID profileId) {
        SagaPrincipal principal = new SagaPrincipal(
                role.name().toLowerCase() + "-subject-" + UUID.randomUUID(),
                role.name().toLowerCase() + "@example.test",
                role.name() + " User",
                role,
                profileId,
                AccountStatus.ACTIVE
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    private record Fixture(
            Lecturer owner,
            Lecturer otherLecturer,
            Course course,
            Course otherCourse,
            Student leader,
            Student member,
            Student mentor,
            Student otherTeamMember,
            Student otherCourseMember
    ) {}
}
