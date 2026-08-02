package com.saga.be;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.integration.identity.IdentityMappingReviewService;
import com.saga.be.integration.identity.PersonalIntegrationService;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CsrfMutationMethodIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityMappingReviewService identityMappingReviewService;

    @MockitoBean
    private PersonalIntegrationService personalIntegrationService;

    @Test
    void patchRequiresTheConfiguredCsrfHeaderBeforeReachingTheController() throws Exception {
        Authentication admin = authenticationFor(ApplicationRole.ADMIN);
        Cookie csrfCookie = csrfCookie(admin);
        UUID mappingId = UUID.randomUUID();

        mockMvc.perform(patch("/api/integrations/identity-mappings/{mappingId}", mappingId)
                        .with(authentication(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/integrations/identity-mappings/{mappingId}", mappingId)
                        .with(authentication(admin))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", "invalid-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/integrations/identity-mappings/{mappingId}", mappingId)
                        .with(authentication(admin))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isOk());

        verify(identityMappingReviewService).review(
                any(SagaPrincipal.class),
                eq(mappingId),
                any(),
                any(String.class)
        );
    }

    @Test
    void deleteRequiresTheConfiguredCsrfHeaderBeforeReachingTheController() throws Exception {
        Authentication student = authenticationFor(ApplicationRole.STUDENT);
        Cookie csrfCookie = csrfCookie(student);

        mockMvc.perform(delete("/api/me/integrations/jira")
                        .with(authentication(student)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/me/integrations/jira")
                        .with(authentication(student))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", "invalid-csrf-token"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/me/integrations/jira")
                        .with(authentication(student))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isNoContent());

        verify(personalIntegrationService).disconnect(
                any(SagaPrincipal.class),
                eq(IntegrationProvider.JIRA),
                any(String.class)
        );
    }

    private Cookie csrfCookie(Authentication authentication) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        if (csrfCookie == null) {
            throw new AssertionError("GET /api/auth/csrf did not create XSRF-TOKEN");
        }
        return csrfCookie;
    }

    private Authentication authenticationFor(ApplicationRole role) {
        SagaPrincipal principal = new SagaPrincipal(
                role.name().toLowerCase() + "-subject",
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
