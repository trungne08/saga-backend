package com.saga.be.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.dto.response.LecturerAnalyticsResponses;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.CourseEarlyWarningQueryService;
import com.saga.be.service.LecturerContributionQueryService;
import com.saga.be.service.LecturerStudentAnalyticsQueryService;
import com.saga.be.service.LecturerTeamAnalyticsQueryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
class LecturerAnalyticsSecurityIntegrationTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean LecturerTeamAnalyticsQueryService teamAnalytics;
    @MockitoBean LecturerStudentAnalyticsQueryService studentAnalytics;
    @MockitoBean LecturerContributionQueryService contributionAnalytics;
    @MockitoBean CourseEarlyWarningQueryService earlyWarnings;

    @Test
    void adminAndLecturerCanReachEveryReadRouteWithoutCsrf() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        for (String path : paths(courseId, teamId, studentId)) {
            mockMvc.perform(get(path).with(authentication(auth(ApplicationRole.ADMIN, UUID.randomUUID()))))
                    .andExpect(status().isOk());
            mockMvc.perform(get(path).with(authentication(auth(ApplicationRole.LECTURER, UUID.randomUUID()))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void studentIsForbiddenAndAnonymousIsUnauthorizedForEveryReadRoute() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        for (String path : paths(courseId, teamId, studentId)) {
            mockMvc.perform(get(path).with(authentication(auth(ApplicationRole.STUDENT, UUID.randomUUID()))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void responseContractDoesNotExposeCredentialIdentityOrVersionFields() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(teamAnalytics.detail(any(), any(), any(), any())).thenReturn(
                new LecturerAnalyticsResponses.TeamDetail(
                        courseId,
                        teamId,
                        "Team",
                        new LecturerAnalyticsResponses.ProjectSummary(
                                UUID.randomUUID(),
                                "Project",
                                List.of(new LecturerAnalyticsResponses.TeamGitHubRepositoryReference(
                                        101L,
                                        "saga/backend"
                                ))
                        ),
                        Page.empty()
                ));
        mockMvc.perform(get("/api/v1/courses/{courseId}/teams/{teamId}/detail", courseId, teamId)
                        .with(authentication(auth(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.repositories[0].repositoryId").value(101))
                .andExpect(jsonPath("$.project.repositories[0].repositoryName").value("saga/backend"))
                .andExpect(jsonPath("$.cognitoSub").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.credential").doesNotExist())
                .andExpect(jsonPath("$.project.repositories[0].url").doesNotExist())
                .andExpect(jsonPath("$.project.repositories[0].installationId").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void invalidPaginationIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/courses/{courseId}/teams/{teamId}/detail",
                        UUID.randomUUID(), UUID.randomUUID()).param("page", "-1")
                        .with(authentication(auth(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isBadRequest());
    }

    private Authentication auth(ApplicationRole role, UUID id) {
        SagaPrincipal principal = new SagaPrincipal("subject", "actor@example.test", "Actor", role, id,
                AccountStatus.ACTIVE);
        return new UsernamePasswordAuthenticationToken(principal, "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private String[] paths(UUID courseId, UUID teamId, UUID studentId) {
        return new String[] {
                "/api/v1/courses/%s/teams/%s/detail".formatted(courseId, teamId),
                "/api/v1/courses/%s/students/%s/progress".formatted(courseId, studentId),
                "/api/v1/courses/%s/students/%s/activities".formatted(courseId, studentId),
                "/api/v1/courses/%s/students/%s/contribution-detail".formatted(courseId, studentId),
                "/api/v1/courses/%s/early-warnings".formatted(courseId),
                "/api/v1/courses/%s/teams/%s/interactions".formatted(courseId, teamId),
                "/api/v1/courses/%s/teams/%s/students/%s/interactions".formatted(courseId, teamId, studentId),
                "/api/v1/courses/%s/teams/%s/overview?startDate=2026-08-01&endDate=2026-08-02".formatted(courseId, teamId),
                "/api/v1/courses/%s/teams/%s/heatmap?startDate=2026-08-01&endDate=2026-08-02".formatted(courseId, teamId),
                "/api/v1/courses/%s/teams/%s/sprints/velocity".formatted(courseId, teamId)
        };
    }
}
