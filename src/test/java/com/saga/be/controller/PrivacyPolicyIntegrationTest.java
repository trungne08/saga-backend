package com.saga.be.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.enums.AccountStatus;
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

@SpringBootTest(properties = "app.privacy.contact-url=https://support.example.test/saga")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
class PrivacyPolicyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousAndAuthenticatedRolesReceiveThePublicHtmlPolicy() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Privacy Policy")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SAGA")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Jira Cloud")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("GitHub")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "https://support.example.test/saga"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("test-secret")
                )));

        for (ApplicationRole role : ApplicationRole.values()) {
            mockMvc.perform(get("/privacy").with(authentication(authenticationFor(role))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void privacyDoesNotOpenMutationOrOtherProtectedApis() throws Exception {
        mockMvc.perform(post("/privacy").with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    private Authentication authenticationFor(ApplicationRole role) {
        SagaPrincipal principal = new SagaPrincipal(
                role.name().toLowerCase() + "-subject-" + UUID.randomUUID(),
                role.name().toLowerCase() + "@example.test",
                role.name() + " User",
                role,
                UUID.randomUUID(),
                AccountStatus.ACTIVE
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }
}
