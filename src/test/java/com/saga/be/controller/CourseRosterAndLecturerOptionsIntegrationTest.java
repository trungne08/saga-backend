package com.saga.be.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Student;
import com.saga.be.entity.StudentCourseInvitation;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.entity.enums.StudentInvitationStatus;
import com.saga.be.entity.enums.StudentInvitationType;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.StudentCourseInvitationRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CourseRosterAndLecturerOptionsIntegrationTest {

    private static final String COURSE_ROSTER_PATH = "/api/v1/courses/{courseId}/students";
    private static final String LECTURER_OPTIONS_PATH = "/api/v1/courses/instructors";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private LecturerRepository lecturerRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private StudentCourseInvitationRepository invitationRepository;

    @AfterEach
    void cleanUp() {
        invitationRepository.deleteAll();
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        studentRepository.deleteAll();
        courseRepository.deleteAll();
        lecturerRepository.deleteAll();
    }

    @Test
    void courseRosterEnforcesAnonymousAdminLecturerAndStudentPolicies() throws Exception {
        Lecturer owner = lecturer("owner", "Owner Lecturer", "owner@example.test");
        Course course = course(owner);
        membership(team(course, "Team A"), student("roster-member", "Roster Member", "member@example.test"));

        mockMvc.perform(get(COURSE_ROSTER_PATH, course.getId()))
                .andExpect(status().isUnauthorized());
        roster(ApplicationRole.ADMIN, UUID.randomUUID(), course)
                .andExpect(status().isOk());
        roster(ApplicationRole.LECTURER, owner.getId(), course)
                .andExpect(status().isOk());
        roster(ApplicationRole.LECTURER, lecturer("other", "Other Lecturer", "other@example.test").getId(), course)
                .andExpect(status().isForbidden());
        roster(ApplicationRole.STUDENT, UUID.randomUUID(), course)
                .andExpect(status().isForbidden());
    }

    @Test
    void courseRosterReturnsNotFoundForMissingCourse() throws Exception {
        roster(ApplicationRole.ADMIN, UUID.randomUUID(), UUID.randomUUID())
                .andExpect(status().isNotFound());
    }

    @Test
    void courseRosterFiltersAndPaginatesTheWholeMembershipDataset() throws Exception {
        Course course = course(lecturer("owner", "Owner", "owner@example.test"));
        Team team = team(course, "Team Filter");
        membership(team, student("match-one", "Match One", "one@example.test"));
        membership(team, student("not-match", "Other", "other@example.test"));
        membership(team, student("match-two", "Match Two", "two@example.test"));

        mockMvc.perform(get(COURSE_ROSTER_PATH, course.getId())
                        .param("keyword", "match")
                        .param("hasTeam", "with")
                        .param("sortBy", "fullName")
                        .param("sortDirection", "asc")
                        .param("page", "1")
                        .param("size", "1")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentsWithTeam.totalElements").value(2))
                .andExpect(jsonPath("$.studentsWithTeam.totalPages").value(2))
                .andExpect(jsonPath("$.studentsWithTeam.number").value(1))
                .andExpect(jsonPath("$.studentsWithTeam.content.length()").value(1))
                .andExpect(jsonPath("$.studentsWithTeam.content[0].fullName").value("Match Two"));
    }

    @Test
    void courseRosterSortsInBothDirectionsWithoutDuplicatingOrSkippingStudentsAcrossPages() throws Exception {
        Course course = course(lecturer("owner", "Owner", "owner@example.test"));
        Team team = team(course, "Team Pages");
        membership(team, student("alpha", "Alpha", "alpha@example.test"));
        membership(team, student("bravo", "Bravo", "bravo@example.test"));
        membership(team, student("zulu", "Zulu", "zulu@example.test"));

        mockMvc.perform(get(COURSE_ROSTER_PATH, course.getId())
                        .param("hasTeam", "with")
                        .param("sortBy", "fullName")
                        .param("sortDirection", "asc")
                        .param("page", "0")
                        .param("size", "2")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentsWithTeam.totalElements").value(3))
                .andExpect(jsonPath("$.studentsWithTeam.content[0].fullName").value("Alpha"))
                .andExpect(jsonPath("$.studentsWithTeam.content[1].fullName").value("Bravo"));
        mockMvc.perform(get(COURSE_ROSTER_PATH, course.getId())
                        .param("hasTeam", "with")
                        .param("sortBy", "fullName")
                        .param("sortDirection", "asc")
                        .param("page", "1")
                        .param("size", "2")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentsWithTeam.content.length()").value(1))
                .andExpect(jsonPath("$.studentsWithTeam.content[0].fullName").value("Zulu"));
        mockMvc.perform(get(COURSE_ROSTER_PATH, course.getId())
                        .param("hasTeam", "with")
                        .param("sortBy", "fullName")
                        .param("sortDirection", "desc")
                        .param("page", "0")
                        .param("size", "1")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentsWithTeam.content[0].fullName").value("Zulu"));
    }

    @Test
    void courseRosterHasTeamContractIsExplicitAndDoesNotTreatOutboxAsEnrollment() throws Exception {
        Course course = course(lecturer("owner", "Owner", "owner@example.test"));
        Student invitedOnly = student("invited-only", "Invited Only", "invited@example.test");
        invitationRepository.save(StudentCourseInvitation.builder()
                .student(invitedOnly)
                .course(course)
                .invitationType(StudentInvitationType.FIRST_LOGIN_REQUIRED)
                .invitationStatus(StudentInvitationStatus.PENDING)
                .build());
        membership(team(course, "Team With"), student("member", "Member", "member@example.test"));

        mockMvc.perform(get(COURSE_ROSTER_PATH, course.getId())
                        .param("hasTeam", "with")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentsWithTeam.totalElements").value(1));
        mockMvc.perform(get(COURSE_ROSTER_PATH, course.getId())
                        .param("hasTeam", "without")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentsWithTeam.totalElements").value(0))
                .andExpect(jsonPath("$.studentsWithoutTeam.totalElements").value(0));
        mockMvc.perform(get(COURSE_ROSTER_PATH, course.getId())
                        .param("hasTeam", "unknown")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void courseRosterRejectsUnsupportedSortAndDirection() throws Exception {
        Course course = course(lecturer("owner", "Owner", "owner@example.test"));

        mockMvc.perform(get(COURSE_ROSTER_PATH, course.getId())
                        .param("sortBy", "cognitoSub")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(COURSE_ROSTER_PATH, course.getId())
                        .param("sortDirection", "sideways")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void courseRosterDoesNotCrashForLegacyInvalidMultipleTeamData() throws Exception {
        // Direct repository setup deliberately simulates legacy invalid data; it is not a valid write-path contract.
        Course course = course(lecturer("owner", "Owner", "owner@example.test"));
        Student sharedStudent = student("shared", "Shared Student", "shared@example.test");
        membership(team(course, "Team First"), sharedStudent);
        membership(team(course, "Team Second"), sharedStudent);

        mockMvc.perform(get(COURSE_ROSTER_PATH, course.getId())
                        .param("hasTeam", "with")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentsWithTeam.content").isArray());
    }

    @Test
    void courseRosterDoesNotExposeInternalIdentityFieldsOrMutateRosterData() throws Exception {
        Course course = course(lecturer("owner", "Owner", "owner@example.test"));
        Team team = team(course, "Team Private");
        membership(team, student("private", "Private Student", "private@example.test"));
        long studentsBefore = studentRepository.count();
        long membershipsBefore = teamMemberRepository.count();
        long invitationsBefore = invitationRepository.count();

        roster(ApplicationRole.ADMIN, UUID.randomUUID(), course)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentsWithTeam.content[0].cognitoSub").doesNotExist())
                .andExpect(jsonPath("$.studentsWithTeam.content[0].version").doesNotExist())
                .andExpect(jsonPath("$.studentsWithTeam.content[0].token").doesNotExist())
                .andExpect(jsonPath("$.studentsWithTeam.content[0].team.teamMembers[0].cognitoSub").doesNotExist());

        org.junit.jupiter.api.Assertions.assertEquals(studentsBefore, studentRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(membershipsBefore, teamMemberRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(invitationsBefore, invitationRepository.count());
    }

    @Test
    void lecturerOptionsEnforceAnonymousAdminLecturerAndStudentPolicies() throws Exception {
        lecturer("option", "Option Lecturer", "option@example.test");

        mockMvc.perform(get(LECTURER_OPTIONS_PATH))
                .andExpect(status().isUnauthorized());
        lecturerOptions(ApplicationRole.ADMIN, UUID.randomUUID())
                .andExpect(status().isOk());
        lecturerOptions(ApplicationRole.LECTURER, UUID.randomUUID())
                .andExpect(status().isForbidden());
        lecturerOptions(ApplicationRole.STUDENT, UUID.randomUUID())
                .andExpect(status().isForbidden());
    }

    @Test
    void lecturerOptionsSearchesTrimmedNameAndEmailButNotCognitoSubject() throws Exception {
        lecturer("internal-subject", "Ada Lovelace", "ada@example.test");
        lecturer("other", "Grace Hopper", "grace@example.test");

        mockMvc.perform(get(LECTURER_OPTIONS_PATH)
                        .param("keyword", "  ADA ")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("ada@example.test"));
        mockMvc.perform(get(LECTURER_OPTIONS_PATH)
                        .param("keyword", "internal-subject")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void lecturerOptionsSortsAndPaginatesWithStableMetadata() throws Exception {
        lecturer("zulu", "Zulu", "zulu@example.test");
        lecturer("alpha", "Alpha", "alpha@example.test");
        lecturer("bravo", "Bravo", "bravo@example.test");

        mockMvc.perform(get(LECTURER_OPTIONS_PATH)
                        .param("sortBy", "fullName")
                        .param("sortDirection", "asc")
                        .param("page", "1")
                        .param("size", "1")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].fullName").value("Bravo"));
        mockMvc.perform(get(LECTURER_OPTIONS_PATH)
                        .param("sortBy", "fullName")
                        .param("sortDirection", "desc")
                        .param("page", "0")
                        .param("size", "1")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fullName").value("Zulu"));
    }

    @Test
    void lecturerOptionsRejectUnsupportedSortAndDoesNotExposeCognitoSubject() throws Exception {
        lecturer("private", "Private Lecturer", "private@example.test");

        mockMvc.perform(get(LECTURER_OPTIONS_PATH)
                        .param("sortBy", "cognitoSub")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(LECTURER_OPTIONS_PATH)
                        .param("sortDirection", "sideways")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isBadRequest());
        lecturerOptions(ApplicationRole.ADMIN, UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].cognitoSub").doesNotExist())
                .andExpect(jsonPath("$.content[0].version").doesNotExist());
    }

    private org.springframework.test.web.servlet.ResultActions roster(
            ApplicationRole role,
            UUID profileId,
            Course course
    ) throws Exception {
        return roster(role, profileId, course.getId());
    }

    private org.springframework.test.web.servlet.ResultActions roster(
            ApplicationRole role,
            UUID profileId,
            UUID courseId
    ) throws Exception {
        return mockMvc.perform(get(COURSE_ROSTER_PATH, courseId)
                .with(authentication(authenticationFor(role, profileId))));
    }

    private org.springframework.test.web.servlet.ResultActions lecturerOptions(
            ApplicationRole role,
            UUID profileId
    ) throws Exception {
        return mockMvc.perform(get(LECTURER_OPTIONS_PATH)
                .with(authentication(authenticationFor(role, profileId))));
    }

    private Lecturer lecturer(String subject, String fullName, String email) {
        return lecturerRepository.save(Lecturer.builder()
                .cognitoSub(subject)
                .fullName(fullName)
                .email(email)
                .build());
    }

    private Course course(Lecturer instructor) {
        return courseRepository.save(Course.builder()
                .courseCode("COURSE-" + UUID.randomUUID())
                .name("Course roster test")
                .instructor(instructor)
                .build());
    }

    private Team team(Course course, String name) {
        return teamRepository.save(Team.builder().course(course).name(name).build());
    }

    private Student student(String subject, String fullName, String email) {
        return studentRepository.save(Student.builder()
                .cognitoSub(subject)
                .fullName(fullName)
                .email(email)
                .studentCode("SE" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private void membership(Team team, Student student) {
        teamMemberRepository.save(TeamMember.builder()
                .team(team)
                .student(student)
                .roleInTeam(RoleInTeam.MEMBER)
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
}
