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
import com.saga.be.repository.ClassRepository;
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
    @Autowired private ClassRepository classRepository;
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
    void leaderMayReadSameTeamCoLeader() throws Exception {
        Fixture fixture = fixture();
        Student coLeader = student("co-leader");
        membership(fixture.team(), coLeader, RoleInTeam.LEADER);
        progress(fixture.course(), coLeader, ApplicationRole.STUDENT, fixture.leader().getId())
                .andExpect(status().isOk());
    }

    @Test
    void leaderMayReadSameTeamMemberWhoAlsoHasAnotherCourseMembership() throws Exception {
        Fixture fixture = fixture();
        membership(fixture.otherTeam(), fixture.member(), RoleInTeam.MEMBER);
        progress(fixture.course(), fixture.member(), ApplicationRole.STUDENT, fixture.leader().getId())
                .andExpect(status().isOk());
        progress(fixture.course(), fixture.otherTeamMember(), ApplicationRole.STUDENT, fixture.leader().getId())
                .andExpect(status().isForbidden());
    }

    @Test
    void leaderWhoAlsoHasAnotherCourseMembershipMayReadSelfButNotUnledTeammate() throws Exception {
        Fixture fixture = fixture();
        membership(fixture.otherTeam(), fixture.leader(), RoleInTeam.MEMBER);
        progress(fixture.course(), fixture.leader(), ApplicationRole.STUDENT, fixture.leader().getId())
                .andExpect(status().isOk());
        progress(fixture.course(), fixture.otherTeamMember(), ApplicationRole.STUDENT, fixture.leader().getId())
                .andExpect(status().isForbidden());
    }

    @Test
    void leaderOfTwoTeamsMayReadMembersOfEachLedTeam() throws Exception {
        Fixture fixture = fixture();
        Team secondLedTeam = team(fixture.course(), "second-led");
        Student secondTeamMember = student("second-led-member");
        membership(secondLedTeam, fixture.leader(), RoleInTeam.LEADER);
        membership(secondLedTeam, secondTeamMember, RoleInTeam.MEMBER);
        progress(fixture.course(), fixture.member(), ApplicationRole.STUDENT, fixture.leader().getId())
                .andExpect(status().isOk());
        progress(fixture.course(), secondTeamMember, ApplicationRole.STUDENT, fixture.leader().getId())
                .andExpect(status().isOk());
        progress(fixture.course(), fixture.otherTeamMember(), ApplicationRole.STUDENT, fixture.leader().getId())
                .andExpect(status().isForbidden());
    }

    @Test
    void memberWithMultipleCourseMembershipsCannotReadOwnProgress() throws Exception {
        Fixture fixture = fixture();
        membership(fixture.otherTeam(), fixture.member(), RoleInTeam.MEMBER);
        progress(fixture.course(), fixture.member(), ApplicationRole.STUDENT, fixture.member().getId())
                .andExpect(status().isConflict());
    }

    @Test
    void leaderInClassACourseAMayReadSameTeamMember() throws Exception {
        ClassScopeFixture scope = classScopeFixture();
        progress(scope.courseA(), scope.memberA(), ApplicationRole.STUDENT, scope.actor().getId())
                .andExpect(status().isOk());
    }

    @Test
    void leaderInClassBCourseBMayReadSameTeamMemberIndependently() throws Exception {
        ClassScopeFixture scope = classScopeFixture();
        progress(scope.courseB(), scope.memberB(), ApplicationRole.STUDENT, scope.actor().getId())
                .andExpect(status().isOk());
    }

    @Test
    void leaderInCourseAWhoIsOnlyMemberInCourseBCannotReadCourseBTeammate() throws Exception {
        ClassScopeFixture scope = classScopeFixture();
        progress(scope.courseC(), scope.memberC(), ApplicationRole.STUDENT, scope.actor().getId())
                .andExpect(status().isForbidden());
    }

    @Test
    void leaderInCourseACannotReadOtherTeamInSameCourse() throws Exception {
        ClassScopeFixture scope = classScopeFixture();
        progress(scope.courseA(), scope.otherTeamAMember(), ApplicationRole.STUDENT, scope.actor().getId())
                .andExpect(status().isForbidden());
    }

    @Test
    void leaderInCourseACannotReadStudentInCourseBUnlessIndependentlyLeaderThere() throws Exception {
        ClassScopeFixture scope = classScopeFixture();
        progress(scope.courseD(), scope.memberD(), ApplicationRole.STUDENT, scope.actor().getId())
                .andExpect(status().isForbidden());
        progress(scope.courseB(), scope.memberB(), ApplicationRole.STUDENT, scope.actor().getId())
                .andExpect(status().isOk());
    }

    @Test
    void leaderRightsAreNotOpenedAcrossCoursesThatShareAClass() throws Exception {
        ClassScopeFixture scope = classScopeFixture();
        progress(scope.courseA2SameClass(), scope.memberA2(), ApplicationRole.STUDENT, scope.actor().getId())
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
        Course course = course(owner, clazz("primary"), "primary");
        Team team = team(course, "primary");
        Team otherTeam = team(course, "same-course-other");
        Course otherCourse = course(lecturer("other-course-owner"), clazz("other-course"), "other-course");
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
        return new Fixture(owner, otherLecturer, course, otherCourse, team, otherTeam, leader, member, mentor, otherTeamMember, otherCourseMember);
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

    private ClassScopeFixture classScopeFixture() {
        Lecturer owner = lecturer("class-scope-owner");
        com.saga.be.entity.Class classA = clazz("class-a");
        com.saga.be.entity.Class classB = clazz("class-b");
        com.saga.be.entity.Class classC = clazz("class-c");
        com.saga.be.entity.Class classD = clazz("class-d");

        Course courseA = course(owner, classA, "class-a");
        Course courseA2SameClass = course(owner, classA, "class-a-other-course");
        Course courseB = course(owner, classB, "class-b");
        Course courseC = course(owner, classC, "class-c-member-only");
        Course courseD = course(owner, classD, "class-d-unrelated");

        Team teamA = team(courseA, "team-a");
        Team otherTeamA = team(courseA, "team-a-other");
        Team teamA2 = team(courseA2SameClass, "team-a2");
        Team teamB = team(courseB, "team-b");
        Team teamC = team(courseC, "team-c");
        Team teamD = team(courseD, "team-d");

        Student actor = student("class-scope-actor");
        Student memberA = student("member-a");
        Student otherTeamAMember = student("other-team-a-member");
        Student memberA2 = student("member-a2");
        Student memberB = student("member-b");
        Student memberC = student("member-c");
        Student memberD = student("member-d");

        membership(teamA, actor, RoleInTeam.LEADER);
        membership(teamA, memberA, RoleInTeam.MEMBER);
        membership(otherTeamA, otherTeamAMember, RoleInTeam.MEMBER);
        membership(teamA2, memberA2, RoleInTeam.MEMBER);
        membership(teamB, actor, RoleInTeam.LEADER);
        membership(teamB, memberB, RoleInTeam.MEMBER);
        membership(teamC, actor, RoleInTeam.MEMBER);
        membership(teamC, memberC, RoleInTeam.MEMBER);
        membership(teamD, memberD, RoleInTeam.MEMBER);

        return new ClassScopeFixture(
                courseA, courseA2SameClass, courseB, courseC, courseD,
                actor, memberA, otherTeamAMember, memberA2, memberB, memberC, memberD);
    }

    private com.saga.be.entity.Class clazz(String label) {
        return classRepository.save(com.saga.be.entity.Class.builder()
                .classCode("CLS-" + label + "-" + UUID.randomUUID())
                .name("Class " + label)
                .build());
    }

    private Course course(Lecturer instructor, com.saga.be.entity.Class clazz, String label) {
        return courseRepository.save(Course.builder()
                .courseCode("PROGRESS-" + label + "-" + UUID.randomUUID())
                .name("Progress " + label)
                .instructor(instructor)
                .clazz(clazz)
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
            Team team,
            Team otherTeam,
            Student leader,
            Student member,
            Student mentor,
            Student otherTeamMember,
            Student otherCourseMember
    ) {}

    private record ClassScopeFixture(
            Course courseA,
            Course courseA2SameClass,
            Course courseB,
            Course courseC,
            Course courseD,
            Student actor,
            Student memberA,
            Student otherTeamAMember,
            Student memberA2,
            Student memberB,
            Student memberC,
            Student memberD
    ) {}
}
