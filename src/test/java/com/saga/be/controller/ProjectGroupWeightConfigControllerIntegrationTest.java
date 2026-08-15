package com.saga.be.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Project;
import com.saga.be.entity.ProjectGroupWeightConfig;
import com.saga.be.entity.Team;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.ProjectGroupWeightConfigRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.math.BigDecimal;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code PUT /api/projects/{projectId}/group-weights} is the Team/Project-scope Contribution
 * weight override, active only while the owning Course is in
 * {@link com.saga.be.entity.enums.ContributionConfigMode#TEAM} mode. Storage stays 0..1
 * (unchanged scale); write access is the exact course-instructor LECTURER or ADMIN only — never
 * the team leader/student, per explicit product instruction not to broaden that authorization.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@Transactional
class ProjectGroupWeightConfigControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LecturerRepository lecturerRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ProjectGroupWeightConfigRepository configRepository;

    @Test
    void courseOwnerLecturerCanCreateAndUpdateAGroupWeightOverride() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(put("/api/projects/{projectId}/group-weights", fixture.project().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fixture.team().getId(), "0.4", "0.1", "0.3", "0.2"))
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId())))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(fixture.project().getId().toString()))
                .andExpect(jsonPath("$.groupId").value(fixture.team().getId().toString()))
                .andExpect(jsonPath("$.codeWeight").value(0.4))
                .andExpect(jsonPath("$.testWeight").value(0.1))
                .andExpect(jsonPath("$.documentWeight").value(0.3))
                .andExpect(jsonPath("$.researchWeight").value(0.2));

        mockMvc.perform(put("/api/projects/{projectId}/group-weights", fixture.project().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fixture.team().getId(), "0.7", "0.1", "0.1", "0.1"))
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId())))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeWeight").value(0.7));

        assertThat(configRepository.findByProjectId(fixture.project().getId())).isPresent();
        ProjectGroupWeightConfig persisted = configRepository.findByProjectId(fixture.project().getId()).orElseThrow();
        assertThat(persisted.getCodeWeight()).isEqualByComparingTo("0.7");
        assertThat(persisted.getDesignWeight()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void adminCanAlsoWriteAGroupWeightOverride() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(put("/api/projects/{projectId}/group-weights", fixture.project().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fixture.team().getId(), "0.25", "0.25", "0.25", "0.25"))
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID())))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void otherLecturerAndStudentLeaderAndAnonymousCannotWrite() throws Exception {
        Fixture fixture = fixture();
        String requestBody = body(fixture.team().getId(), "0.4", "0.1", "0.3", "0.2");

        mockMvc.perform(put("/api/projects/{projectId}/group-weights", fixture.project().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.otherLecturer().getId())))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/projects/{projectId}/group-weights", fixture.project().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(authentication(authenticationFor(ApplicationRole.STUDENT, UUID.randomUUID())))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/projects/{projectId}/group-weights", fixture.project().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void groupIdMustBelongToTheProjectsOwningTeam() throws Exception {
        Fixture fixture = fixture();
        UUID unrelatedTeamId = UUID.randomUUID();

        mockMvc.perform(put("/api/projects/{projectId}/group-weights", fixture.project().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(unrelatedTeamId, "0.4", "0.1", "0.3", "0.2"))
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId())))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("GROUP_PROJECT_MISMATCH"));
    }

    @Test
    void negativeOrOutOfRangeOrNonUnitSumWeightsAreRejected() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(put("/api/projects/{projectId}/group-weights", fixture.project().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fixture.team().getId(), "-0.1", "0.3", "0.4", "0.4"))
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId())))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("GROUP_WEIGHT_OUT_OF_RANGE"));

        mockMvc.perform(put("/api/projects/{projectId}/group-weights", fixture.project().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fixture.team().getId(), "1.1", "0.3", "0.4", "0.4"))
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId())))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("GROUP_WEIGHT_OUT_OF_RANGE"));

        mockMvc.perform(put("/api/projects/{projectId}/group-weights", fixture.project().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fixture.team().getId(), "0.4", "0.1", "0.3", "0.3"))
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId())))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("GROUP_WEIGHT_SUM_INVALID"));
    }

    @Test
    void zeroWeightOnASingleCriterionIsAccepted() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(put("/api/projects/{projectId}/group-weights", fixture.project().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fixture.team().getId(), "0.0", "0.4", "0.3", "0.3"))
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId())))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeWeight").value(0.0));
    }

    @Test
    void historicalRowsRemainReadableThroughTheRetainedRepository() {
        Lecturer instructor = lecturerRepository.saveAndFlush(Lecturer.builder()
                .cognitoSub(unique("OWNER-SUB"))
                .email(unique("owner") + "@example.test")
                .fullName("Owner Lecturer")
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        Course course = courseRepository.saveAndFlush(Course.builder()
                .instructor(instructor)
                .courseCode(unique("COURSE"))
                .name("Historical group weight course")
                .build());
        Project project = projectRepository.saveAndFlush(Project.builder()
                .course(course)
                .name("Historical group weight project")
                .build());
        Team team = teamRepository.saveAndFlush(Team.builder()
                .course(course)
                .project(project)
                .name(unique("Team"))
                .build());

        ProjectGroupWeightConfig saved = configRepository.saveAndFlush(ProjectGroupWeightConfig.builder()
                .project(project)
                .team(team)
                .codeWeight(new BigDecimal("0.5"))
                .testWeight(new BigDecimal("0.0"))
                .documentWeight(new BigDecimal("0.3"))
                .researchWeight(new BigDecimal("0.0"))
                .designWeight(new BigDecimal("0.2"))
                .build());

        assertThat(configRepository.findByProjectId(project.getId())).isPresent();
        assertThat(configRepository.findById(saved.getId())).isPresent();
    }

    private String body(UUID groupId, String code, String test, String document, String research) {
        return """
                {
                  "groupId": "%s",
                  "codeWeight": %s,
                  "testWeight": %s,
                  "documentWeight": %s,
                  "researchWeight": %s
                }
                """.formatted(groupId, code, test, document, research);
    }

    private Fixture fixture() {
        Lecturer owner = lecturerRepository.saveAndFlush(Lecturer.builder()
                .cognitoSub(unique("OWNER-SUB"))
                .email(unique("owner") + "@example.test")
                .fullName("Owner Lecturer")
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        Lecturer otherLecturer = lecturerRepository.saveAndFlush(Lecturer.builder()
                .cognitoSub(unique("OTHER-SUB"))
                .email(unique("other") + "@example.test")
                .fullName("Other Lecturer")
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        Course course = courseRepository.saveAndFlush(Course.builder()
                .instructor(owner)
                .courseCode(unique("COURSE"))
                .name("Group weight course")
                .build());
        Project project = projectRepository.saveAndFlush(Project.builder()
                .course(course)
                .name("Group weight project")
                .build());
        Team team = teamRepository.saveAndFlush(Team.builder()
                .course(course)
                .project(project)
                .name(unique("Team"))
                .build());
        return new Fixture(owner, otherLecturer, project, team);
    }

    private Authentication authenticationFor(ApplicationRole role, UUID localProfileId) {
        SagaPrincipal principal = new SagaPrincipal(
                role.name().toLowerCase() + "-group-weight-" + UUID.randomUUID(),
                role.name().toLowerCase() + "@example.test",
                role.name() + " User",
                role,
                localProfileId,
                AccountStatus.ACTIVE
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private record Fixture(Lecturer owner, Lecturer otherLecturer, Project project, Team team) {}
}
