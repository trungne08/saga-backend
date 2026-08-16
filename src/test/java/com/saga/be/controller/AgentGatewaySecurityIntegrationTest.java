package com.saga.be.controller;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.dto.response.AgentApiResponses;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.InternalAiServiceAuthenticationFilter;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.AgentGatewayService;
import com.saga.be.service.AgentDelegationCapability;
import com.saga.be.service.AgentDelegationService;
import com.saga.be.service.AgentToolProjectionService;
import com.saga.be.service.CurrentAccountStatusService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.internal-ai.service-token=synthetic-ai-to-backend-token-1234567890"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
class AgentGatewaySecurityIntegrationTest {

    private static final String INTERNAL_TOKEN = "synthetic-ai-to-backend-token-1234567890";
    private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000123");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentGatewayService gateway;

    @MockitoBean
    private AgentToolProjectionService projections;

    @MockitoBean
    private com.saga.be.service.AgentRoleAwareProjectionService roleAware;

    @MockitoBean
    private AgentDelegationService delegations;

    @MockitoBean
    private CurrentAccountStatusService accountStatuses;

    @BeforeEach
    void reset() {
        clearInvocations(gateway, projections, roleAware, delegations, accountStatuses);
        when(accountStatuses.isAllowedForBusinessApi(any())).thenReturn(true);
        when(gateway.create(any(), any())).thenReturn(conversation());
        when(gateway.list(any())).thenReturn(new AgentApiResponses.ConversationList(List.of()));
    }

    @Test
    void anonymousAndServiceCredentialAloneCannotUseBrowserGateway() throws Exception {
        mockMvc.perform(post("/api/v1/ai/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/ai/conversations")
                        .header(InternalAiServiceAuthenticationFilter.HEADER_NAME, INTERNAL_TOKEN))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(gateway);
    }

    @Test
    void unsafeGatewayRequiresSessionCsrfAndDoesNotAcceptActorOverride() throws Exception {
        Authentication actor = authFor(ApplicationRole.ADMIN);
        mockMvc.perform(post("/api/v1/ai/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(authentication(actor)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ai/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actorId\":\"override\"}")
                        .with(authentication(actor))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/ai/conversations/" + CONVERSATION_ID + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\",\"actorId\":\"override\",\"applicationRole\":\"ADMIN\",\"studentId\":\""
                                + UUID.randomUUID() + "\"}")
                        .with(authentication(actor))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/ai/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Private\"}")
                        .with(authentication(actor))
                        .with(csrf()))
                .andExpect(status().isCreated());
    }

    @Test
    void readsNeedSessionButNotCsrfAndBlockedCurrentAccountIsDenied() throws Exception {
        Authentication student = authFor(ApplicationRole.STUDENT);
        mockMvc.perform(get("/api/v1/ai/conversations").with(authentication(student)))
                .andExpect(status().isOk());

        when(accountStatuses.isAllowedForBusinessApi(any())).thenReturn(false);
        mockMvc.perform(get("/api/v1/ai/conversations").with(authentication(student)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"));
    }

    @Test
    void internalToolNeedsBothAiCredentialAndOpaqueDelegationContext() throws Exception {
        String path = "/internal/ai/v1/agent/tools/project-summary";
        String body = "{\"conversationId\":\"" + CONVERSATION_ID
                + "\",\"projectId\":\"" + UUID.randomUUID() + "\"}";
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(path)
                        .header(InternalAiServiceAuthenticationFilter.HEADER_NAME, "wrong-token-value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(path)
                        .header(InternalAiServiceAuthenticationFilter.HEADER_NAME, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString(INTERNAL_TOKEN))));
        verifyNoInteractions(projections);
    }

    @Test
    void resourceDiscoveryUsesServiceCredentialAndDelegatedCurrentActor() throws Exception {
        SagaPrincipal student = (SagaPrincipal) authFor(ApplicationRole.STUDENT).getPrincipal();
        String opaqueContext = "opaque-delegated-context-value-1234567890";
        when(delegations.resolveAccess(
                opaqueContext, CONVERSATION_ID, AgentDelegationCapability.READ
        )).thenReturn(new com.saga.be.service.AgentDelegatedAccess(student, null));
        when(projections.resourceContext(student, null)).thenReturn(
                new com.saga.be.dto.response.InternalAgentToolResponses.ResourceContext(
                        "STUDENT", "ZERO_MATCH", 0, 0, 0, List.of(), List.of(), null, null
                )
        );

        mockMvc.perform(post("/internal/ai/v1/agent/tools/resource-context")
                        .header(InternalAiServiceAuthenticationFilter.HEADER_NAME, INTERNAL_TOKEN)
                        .header(InternalAgentToolController.DELEGATED_CONTEXT_HEADER, opaqueContext)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + CONVERSATION_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ZERO_MATCH")));
    }

    @Test
    void resolveAssigneeUsesServiceCredentialDelegatedActorAndDoesNotLeakProviderIdentity() throws Exception {
        SagaPrincipal actor = (SagaPrincipal) authFor(ApplicationRole.STUDENT).getPrincipal();
        String opaqueContext = "opaque-delegated-context-value-1234567890";
        UUID projectId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(delegations.resolveAccess(
                opaqueContext, CONVERSATION_ID, AgentDelegationCapability.READ
        )).thenReturn(new com.saga.be.service.AgentDelegatedAccess(actor, null));
        when(projections.resolveAssignee(actor, projectId, null, "SE123456")).thenReturn(
                new com.saga.be.dto.response.InternalAgentToolResponses.AssigneeResolution(
                        projectId, UUID.randomUUID(), "MATCHED",
                        List.of(new com.saga.be.dto.response.InternalAgentToolResponses.AssigneeCandidate(
                                studentId, "Le Hoang Hai", "SE123456"
                        ))
                )
        );

        mockMvc.perform(post("/internal/ai/v1/agent/tools/resolve-assignee")
                        .header(InternalAiServiceAuthenticationFilter.HEADER_NAME, INTERNAL_TOKEN)
                        .header(InternalAgentToolController.DELEGATED_CONTEXT_HEADER, opaqueContext)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + CONVERSATION_ID
                                + "\",\"projectId\":\"" + projectId
                                + "\",\"studentCode\":\"SE123456\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("MATCHED")))
                .andExpect(content().string(containsString(studentId.toString())))
                .andExpect(content().string(not(containsString("email"))))
                .andExpect(content().string(not(containsString("cognito"))))
                .andExpect(content().string(not(containsString("accountId"))));
    }

    @Test
    void selfProgressRejectsBrowserIdentityFieldsAndUsesDelegatedActor() throws Exception {
        SagaPrincipal student = (SagaPrincipal) authFor(ApplicationRole.STUDENT).getPrincipal();
        String opaqueContext = "opaque-delegated-context-value-1234567890";
        when(delegations.resolveAccess(
                opaqueContext, CONVERSATION_ID, AgentDelegationCapability.READ
        )).thenReturn(new com.saga.be.service.AgentDelegatedAccess(student, null));
        when(roleAware.selfProgress(student, null, null)).thenReturn(
                new com.saga.be.dto.response.InternalAgentToolResponses.SelfProgress(
                        "ZERO_MATCH", null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of()
                )
        );

        mockMvc.perform(post("/internal/ai/v1/agent/tools/self-progress")
                        .header(InternalAiServiceAuthenticationFilter.HEADER_NAME, INTERNAL_TOKEN)
                        .header(InternalAgentToolController.DELEGATED_CONTEXT_HEADER, opaqueContext)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + CONVERSATION_ID
                                + "\",\"studentId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/internal/ai/v1/agent/tools/self-progress")
                        .header(InternalAiServiceAuthenticationFilter.HEADER_NAME, INTERNAL_TOKEN)
                        .header(InternalAgentToolController.DELEGATED_CONTEXT_HEADER, opaqueContext)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + CONVERSATION_ID + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void publicAiUnauthenticatedUsesSessionExpiredMessage() throws Exception {
        mockMvc.perform(get("/api/v1/ai/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Phiên đăng nhập đã hết hạn."));
    }

    @Test
    void internalAiMissingServiceTokenDoesNotLookLikeUserSessionExpiry() throws Exception {
        mockMvc.perform(post("/internal/ai/v1/agent/tools/self-progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + CONVERSATION_ID + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INTERNAL_SERVICE_AUTHENTICATION_REQUIRED"))
                .andExpect(content().string(not(containsString("Phiên đăng nhập đã hết hạn."))));
    }

    @Test
    void memberLecturerReportIsForbiddenWithSafeDenial() throws Exception {
        SagaPrincipal student = (SagaPrincipal) authFor(ApplicationRole.STUDENT).getPrincipal();
        String opaqueContext = "opaque-delegated-context-value-1234567890";
        when(delegations.resolveAccess(
                opaqueContext, CONVERSATION_ID, AgentDelegationCapability.READ
        )).thenReturn(new com.saga.be.service.AgentDelegatedAccess(student, null));
        when(roleAware.lecturerProgressReport(student, null))
                .thenThrow(new AccessDeniedException("This tool is available only to the current Lecturer"));

        mockMvc.perform(post("/internal/ai/v1/agent/tools/lecturer-progress-report")
                        .header(InternalAiServiceAuthenticationFilter.HEADER_NAME, INTERNAL_TOKEN)
                        .header(InternalAgentToolController.DELEGATED_CONTEXT_HEADER, opaqueContext)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + CONVERSATION_ID + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập hoặc thực hiện thao tác này."));
    }

    @Test
    void nonAdminSystemReportIsForbiddenWithSafeDenial() throws Exception {
        SagaPrincipal lecturer = (SagaPrincipal) authFor(ApplicationRole.LECTURER).getPrincipal();
        String opaqueContext = "opaque-delegated-context-value-1234567890";
        when(delegations.resolveAccess(
                opaqueContext, CONVERSATION_ID, AgentDelegationCapability.READ
        )).thenReturn(new com.saga.be.service.AgentDelegatedAccess(lecturer, null));
        when(roleAware.adminSystemReport(lecturer))
                .thenThrow(new AccessDeniedException("Admin system report is available only to ADMIN"));

        mockMvc.perform(post("/internal/ai/v1/agent/tools/admin-system-report")
                        .header(InternalAiServiceAuthenticationFilter.HEADER_NAME, INTERNAL_TOKEN)
                        .header(InternalAgentToolController.DELEGATED_CONTEXT_HEADER, opaqueContext)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + CONVERSATION_ID + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập hoặc thực hiện thao tác này."));
    }

    @Test
    void nonAdminSystemContextIsForbiddenWithSafeDenial() throws Exception {
        SagaPrincipal lecturer = (SagaPrincipal) authFor(ApplicationRole.LECTURER).getPrincipal();
        String opaqueContext = "opaque-delegated-context-value-1234567890";
        when(delegations.resolveAccess(
                opaqueContext, CONVERSATION_ID, AgentDelegationCapability.READ
        )).thenReturn(new com.saga.be.service.AgentDelegatedAccess(lecturer, null));
        when(roleAware.adminSystemReport(lecturer))
                .thenThrow(new AccessDeniedException("Admin system report is available only to ADMIN"));

        mockMvc.perform(post("/internal/ai/v1/agent/tools/admin-system-context")
                        .header(InternalAiServiceAuthenticationFilter.HEADER_NAME, INTERNAL_TOKEN)
                        .header(InternalAgentToolController.DELEGATED_CONTEXT_HEADER, opaqueContext)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + CONVERSATION_ID + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập hoặc thực hiện thao tác này."));
    }

    private AgentApiResponses.Conversation conversation() {
        return new AgentApiResponses.Conversation(
                CONVERSATION_ID, "Private", null, "ADMIN", false,
                "2026-08-14T00:00:00Z", "2026-08-14T00:00:00Z", List.of()
        );
    }

    private Authentication authFor(ApplicationRole role) {
        SagaPrincipal principal = new SagaPrincipal(
                role.name().toLowerCase() + "-agent-sub",
                role.name().toLowerCase() + "@example.test",
                role.name(), role, UUID.randomUUID(), AccountStatus.ACTIVE
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }
}
