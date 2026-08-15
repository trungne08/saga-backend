package com.saga.be.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@Transactional
class TeamContributionAuthorizationIntegrationTest {

    private static final String EVALUATION_PATH = "/api/v1/teams/{teamId}/contribution-evaluation";
    private static final String OVERRIDE_PATH = "/api/v1/teams/{teamId}/contribution-override";

    @Autowired private MockMvc mockMvc;
    @Autowired private LecturerRepository lecturerRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;

    @Test
    void adminMayReadAnyTeamAndResponseContainsOnlyApprovedContributionFields() throws Exception {
        Fixture fixture = fixture();

        request(ApplicationRole.ADMIN, UUID.randomUUID(), fixture.team())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(fixture.team().getId().toString()))
                .andExpect(jsonPath("$.projectId").value(fixture.project().getId().toString()))
                .andExpect(jsonPath("$.evaluatedAt").exists())
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].studentId").exists())
                .andExpect(jsonPath("$.members[0].fullName").exists())
                .andExpect(jsonPath("$.members[0].studentCode").exists())
                .andExpect(jsonPath("$.members[0].finalContributionPercentage").value(50.0))
                .andExpect(jsonPath("$.members[0].email").doesNotExist())
                .andExpect(jsonPath("$.members[0].cognitoSub").doesNotExist())
                .andExpect(jsonPath("$.members[0].comment").doesNotExist())
                .andExpect(jsonPath("$.members[0].reviewerId").doesNotExist())
                .andExpect(jsonPath("$.members[0].token").doesNotExist());
    }

    @Test
    void owningLecturerMayReadAndOtherLecturerIsForbidden() throws Exception {
        Fixture fixture = fixture();

        request(ApplicationRole.LECTURER, fixture.owner().getId(), fixture.team())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].finalContributionPercentage").value(50.0));
        request(ApplicationRole.LECTURER, lecturer("other").getId(), fixture.team())
                .andExpect(status().isForbidden());
    }

    @Test
    void exactTeamLeaderMayReadWithoutChangingTheAggregate() throws Exception {
        Fixture fixture = fixture();

        MvcResult adminResult = request(ApplicationRole.ADMIN, UUID.randomUUID(), fixture.team())
                .andExpect(status().isOk())
                .andReturn();
        request(ApplicationRole.STUDENT, fixture.leader().getId(), fixture.team())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].finalContributionPercentage").value(50.0))
                .andExpect(jsonPath("$.members[1].finalContributionPercentage").value(50.0))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertEquals(
                        contributionPercentages(adminResult),
                        contributionPercentages(result)
                ));
    }

    @Test
    void memberAndMentorOfTheExactTeamAreForbidden() throws Exception {
        Fixture fixture = fixture();
        Student mentor = student("mentor");
        membership(fixture.team(), mentor, RoleInTeam.MENTOR);

        request(ApplicationRole.STUDENT, fixture.member().getId(), fixture.team())
                .andExpect(status().isForbidden());
        request(ApplicationRole.STUDENT, mentor.getId(), fixture.team())
                .andExpect(status().isForbidden());
    }

    @Test
    void leaderOfAnotherTeamEvenInTheSameCourseIsForbidden() throws Exception {
        Fixture fixture = fixture();
        Team otherTeam = teamRepository.save(Team.builder()
                .course(fixture.course())
                .name("Other Team " + UUID.randomUUID())
                .build());
        Student otherLeader = student("other-leader");
        membership(otherTeam, otherLeader, RoleInTeam.LEADER);

        request(ApplicationRole.STUDENT, otherLeader.getId(), fixture.team())
                .andExpect(status().isForbidden());
    }

    @Test
    void studentWithoutMembershipIsForbidden() throws Exception {
        Fixture fixture = fixture();

        request(ApplicationRole.STUDENT, student("outside").getId(), fixture.team())
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsUnauthorizedAndMissingTeamKeepsNotFoundSemantics() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(get(EVALUATION_PATH, fixture.team().getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(EVALUATION_PATH, UUID.randomUUID())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void leaderStillCannotRequestContributionOverride() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(post(OVERRIDE_PATH, fixture.team().getId())
                        .with(authentication(authenticationFor(
                                ApplicationRole.STUDENT,
                                fixture.leader().getId()
                        )))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentId": "%s",
                                  "proposedPercentage": 60,
                                  "reason": "Not permitted"
                                }
                                """.formatted(fixture.leader().getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiDocumentsConditionalStudentLeaderAccess() throws Exception {
        String operation = "$.paths['/api/v1/teams/{teamId}/contribution-evaluation'].get";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".description").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("STUDENT"),
                        org.hamcrest.Matchers.containsString("LEADER"),
                        org.hamcrest.Matchers.containsString("MEMBER"),
                        org.hamcrest.Matchers.containsString("MENTOR")
                )))
                .andExpect(jsonPath(operation + ".responses['200']").exists())
                .andExpect(jsonPath(operation + ".responses['401']").exists())
                .andExpect(jsonPath(operation + ".responses['403']").exists())
                .andExpect(jsonPath(operation + ".responses['404']").exists());
    }

    private org.springframework.test.web.servlet.ResultActions request(
            ApplicationRole role,
            UUID profileId,
            Team team
    ) throws Exception {
        return mockMvc.perform(get(EVALUATION_PATH, team.getId())
                .with(authentication(authenticationFor(role, profileId))));
    }

    private Fixture fixture() {
        Lecturer owner = lecturer("owner");
        Course course = courseRepository.save(Course.builder()
                .courseCode("CONTRIBUTION-" + UUID.randomUUID())
                .name("Contribution Course")
                .instructor(owner)
                .build());
        Project project = projectRepository.save(Project.builder()
                .course(course)
                .name("Contribution Project")
                .build());
        Team team = teamRepository.save(Team.builder()
                .course(course)
                .project(project)
                .name("Contribution Team")
                .build());
        Student leader = student("leader");
        Student member = student("member");
        membership(team, leader, RoleInTeam.LEADER);
        membership(team, member, RoleInTeam.MEMBER);
        return new Fixture(owner, course, project, team, leader, member);
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

    private List<Double> contributionPercentages(MvcResult result) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString())
                .path("members")
                .findValues("finalContributionPercentage")
                .stream()
                .map(com.fasterxml.jackson.databind.JsonNode::asDouble)
                .toList();
    }

    private record Fixture(
            Lecturer owner,
            Course course,
            Project project,
            Team team,
            Student leader,
            Student member
    ) {}
}
