package com.saga.be.controller;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.InternalAiServiceAuthenticationFilter;
import com.saga.be.security.SagaPrincipal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.internal-ai.service-token=synthetic-internal-service-token-1234567890",
        "springdoc.api-docs.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
class InternalCommitReviewContextSecurityIntegrationTest {

    private static final String SERVICE_TOKEN =
            "synthetic-internal-service-token-1234567890";
    private static final String PATH = "/internal/ai/v1/projects/"
            + "00000000-0000-0000-0000-000000000001"
            + "/github/repositories/42/commits/"
            + "0123456789abcdef0123456789abcdef01234567/review-context";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void missingServiceCredentialIsRejectedWithoutEchoingConfiguredSecret() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error")
                        .value("INTERNAL_SERVICE_AUTHENTICATION_REQUIRED"))
                .andExpect(content().string(not(containsString(SERVICE_TOKEN))));
    }

    @Test
    void wrongServiceCredentialIsRejectedWithoutEchoingPresentedSecret() throws Exception {
        String wrong = "synthetic-wrong-service-token-123456789012";
        mockMvc.perform(get(PATH)
                        .header(InternalAiServiceAuthenticationFilter.HEADER_NAME, wrong))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString(wrong))))
                .andExpect(content().string(not(containsString(SERVICE_TOKEN))));
    }

    @Test
    void correctServiceCredentialReachesOnlyTheInternalContextContract() throws Exception {
        mockMvc.perform(get(PATH)
                        .header(
                                InternalAiServiceAuthenticationFilter.HEADER_NAME,
                                SERVICE_TOKEN
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SAGA_PROJECT_NOT_FOUND"))
                .andExpect(content().string(not(containsString(SERVICE_TOKEN))));

        mockMvc.perform(get("/api/v1/subjects")
                        .header(
                                InternalAiServiceAuthenticationFilter.HEADER_NAME,
                                SERVICE_TOKEN
                        ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void browserAuthenticationDoesNotReplaceInternalServiceCredential() throws Exception {
        SagaPrincipal principal = new SagaPrincipal(
                "browser-sub",
                "browser@example.test",
                "Browser User",
                ApplicationRole.ADMIN,
                UUID.randomUUID(),
                AccountStatus.ACTIVE
        );
        var browser = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        mockMvc.perform(get(PATH).with(authentication(browser)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error")
                        .value("INTERNAL_SERVICE_AUTHENTICATION_REQUIRED"));
    }

    @Test
    void healthRemainsPublicAndIndependentFromInternalCredential() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void internalContextContractIsNotPublishedInBrowserOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/internal/ai/"))));
    }
}
