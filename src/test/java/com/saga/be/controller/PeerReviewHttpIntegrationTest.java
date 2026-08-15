package com.saga.be.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.dto.request.PeerReviewRequest;
import com.saga.be.dto.response.PeerReviewCandidatesResponse;
import com.saga.be.dto.response.PeerReviewDefaultRubricResponse;
import com.saga.be.dto.response.PeerReviewResponse;
import com.saga.be.dto.response.PeerReviewRubricResponse;
import com.saga.be.dto.response.SprintPeerReviewResponse;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.PeerReviewService;
import java.time.LocalDateTime;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PeerReviewHttpIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PeerReviewService peerReviewService;

    @Test
    void anonymousCannotReadPeerReviewsOrRubrics() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews", teamId, sprintId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/teams/{teamId}/peer-review-rubric", teamId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/peer-review-rubrics/default"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(peerReviewService);
    }

    @Test
    void lecturerCannotSubmitPeerReview() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews", teamId, sprintId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson(UUID.randomUUID()))
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER)))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(peerReviewService);
    }

    @Test
    void studentSubmitRequiresValidCsrfBeforeServiceCall() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();
        Authentication authentication = authenticationFor(ApplicationRole.STUDENT);

        mockMvc.perform(post("/api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews", teamId, sprintId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson(UUID.randomUUID()))
                        .with(authentication(authentication)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews", teamId, sprintId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson(UUID.randomUUID()))
                        .with(authentication(authentication))
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(peerReviewService);
    }

    @Test
    void invalidPeerReviewBodyReturnsBadRequestWithoutServiceCall() throws Exception {
        mockMvc.perform(post("/api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews",
                        UUID.randomUUID(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revieweeId\":null,\"starRating\":6}")
                        .with(authentication(authenticationFor(ApplicationRole.STUDENT)))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(peerReviewService);
    }

    @Test
    void studentCanSubmitAndPrincipalPathAndBodyAreDelegated() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();
        UUID revieweeId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        Authentication authentication = authenticationFor(ApplicationRole.STUDENT);
        PeerReviewResponse response = new PeerReviewResponse(
                responseId, sprintId, "Sprint 1", principal(authentication).localProfileId(),
                "Reviewer", revieweeId, "Reviewee", 4, List.of(), "Solid work",
                LocalDateTime.of(2026, 8, 15, 10, 0), LocalDateTime.of(2026, 8, 15, 10, 0)
        );
        when(peerReviewService.submit(any(), eq(teamId), eq(sprintId), any(PeerReviewRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews", teamId, sprintId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson(revieweeId))
                        .with(authentication(authentication))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(responseId.toString()))
                .andExpect(jsonPath("$.revieweeId").value(revieweeId.toString()))
                .andExpect(jsonPath("$.starRating").value(4));

        verify(peerReviewService).submit(
                eq(principal(authentication)), eq(teamId), eq(sprintId),
                org.mockito.ArgumentMatchers.argThat(request ->
                        revieweeId.equals(request.getRevieweeId())
                                && request.getStarRating() == 4
                                && "Solid work".equals(request.getComment()))
        );
    }

    @Test
    void authenticatedReadsDelegateCandidatesReviewsAndRubrics() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();
        Authentication student = authenticationFor(ApplicationRole.STUDENT);
        Authentication lecturer = authenticationFor(ApplicationRole.LECTURER);
        when(peerReviewService.getReviewCandidates(principal(student), teamId, sprintId))
                .thenReturn(new PeerReviewCandidatesResponse(
                        teamId, sprintId, principal(student).localProfileId(), List.of()
                ));
        when(peerReviewService.getSprintReviews(principal(lecturer), teamId, sprintId))
                .thenReturn(new SprintPeerReviewResponse(teamId, sprintId, "Sprint 1", List.of()));
        when(peerReviewService.getPeerReviewRubric(principal(student), teamId))
                .thenReturn(new PeerReviewRubricResponse(teamId, UUID.randomUUID(), List.of()));
        when(peerReviewService.getDefaultPeerReviewRubric())
                .thenReturn(new PeerReviewDefaultRubricResponse(List.of()));

        mockMvc.perform(get("/api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews/candidates", teamId, sprintId)
                        .with(authentication(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(teamId.toString()))
                .andExpect(jsonPath("$.candidates").isEmpty());
        mockMvc.perform(get("/api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews", teamId, sprintId)
                        .with(authentication(lecturer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sprintId").value(sprintId.toString()))
                .andExpect(jsonPath("$.reviews").isEmpty());
        mockMvc.perform(get("/api/v1/teams/{teamId}/peer-review-rubric", teamId)
                        .with(authentication(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(teamId.toString()))
                .andExpect(jsonPath("$.criteria").isEmpty());
        mockMvc.perform(get("/api/v1/peer-review-rubrics/default")
                        .with(authentication(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criteria").isEmpty());

        verify(peerReviewService).getReviewCandidates(principal(student), teamId, sprintId);
        verify(peerReviewService).getSprintReviews(principal(lecturer), teamId, sprintId);
        verify(peerReviewService).getPeerReviewRubric(principal(student), teamId);
        verify(peerReviewService).getDefaultPeerReviewRubric();
    }

    private String validRequestJson(UUID revieweeId) {
        return "{\"revieweeId\":\"" + revieweeId + "\",\"starRating\":4,\"comment\":\"Solid work\"}";
    }

    private Authentication authenticationFor(ApplicationRole role) {
        SagaPrincipal principal = new SagaPrincipal(
                role.name().toLowerCase(), role.name() + "@example.test", role.name(), role,
                UUID.randomUUID(), AccountStatus.ACTIVE
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    private SagaPrincipal principal(Authentication authentication) {
        return (SagaPrincipal) authentication.getPrincipal();
    }
}
