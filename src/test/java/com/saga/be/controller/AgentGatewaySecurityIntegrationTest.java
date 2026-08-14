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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.dto.response.AgentApiResponses;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.InternalAiServiceAuthenticationFilter;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.AgentGatewayService;
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
    private CurrentAccountStatusService accountStatuses;

    @BeforeEach
    void reset() {
        clearInvocations(gateway, projections, accountStatuses);
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
                .andExpect(status().isForbidden());
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

    private AgentApiResponses.Conversation conversation() {
        return new AgentApiResponses.Conversation(
                CONVERSATION_ID, "Private", "ADMIN", false,
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
