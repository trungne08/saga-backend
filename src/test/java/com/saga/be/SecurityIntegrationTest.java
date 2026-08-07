package com.saga.be;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;

import com.saga.be.security.ApplicationRole;
import com.saga.be.security.NoStoreOAuth2AuthorizedClientRepository;
import com.saga.be.security.SagaPrincipal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void loginAndHealthArePublic() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "/oauth2/authorization/cognito"
                ));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void landingPageIsPublicAndLinksToOperationalEndpoints() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SAGA Backend")))
                .andExpect(content().string(containsString("/swagger-ui/index.html")))
                .andExpect(content().string(containsString("/actuator/health")));
    }

    @Test
    void applicationApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/subjects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Authentication is required"));

        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void disabledIntegrationEndpointsReturnSafeErrorsWhileCognitoApisWork()
            throws Exception {
        Authentication student = authenticationFor(ApplicationRole.STUDENT);

        mockMvc.perform(get("/api/me/integrations/jira/connect")
                        .with(authentication(student)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error")
                        .value("INTEGRATION_NOT_CONFIGURED"));

        mockMvc.perform(get("/api/me/integrations/github/connect")
                        .with(authentication(student)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error")
                        .value("INTEGRATION_NOT_CONFIGURED"));

        mockMvc.perform(post("/api/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error")
                        .value("INTEGRATION_NOT_CONFIGURED"));

        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isFound());
    }

    @Test
    void csrfExemptionIsLimitedToTheTwoWebhookPostEndpoints() throws Exception {
        mockMvc.perform(post("/api/webhooks/not-a-webhook")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isForbidden());
    }

    @Test
    void oauthAuthorizedClientStorageIsDeliberatelyDisabled() {
        Map<String, OAuth2AuthorizedClientRepository> repositories =
                applicationContext.getBeansOfType(OAuth2AuthorizedClientRepository.class);
        Map<String, OAuth2AuthorizedClientService> services =
                applicationContext.getBeansOfType(OAuth2AuthorizedClientService.class);

        assertEquals(1, repositories.size());
        assertEquals(1, services.size());
        Object repository = repositories.values().iterator().next();
        Object service = services.values().iterator().next();
        assertSame(repository, service);
        assertInstanceOf(NoStoreOAuth2AuthorizedClientRepository.class, repository);
    }

    @Test
    void oauthCallbackKeepsOriginalOidcAuthenticationRequestScoped() {
        FilterChainProxy securityFilterChain = applicationContext.getBean(
                FilterChainProxy.class
        );
        OAuth2LoginAuthenticationFilter oauthFilter = securityFilterChain
                .getFilterChains()
                .stream()
                .flatMap(chain -> chain.getFilters().stream())
                .filter(OAuth2LoginAuthenticationFilter.class::isInstance)
                .map(OAuth2LoginAuthenticationFilter.class::cast)
                .findFirst()
                .orElseThrow();

        Object contextRepository = ReflectionTestUtils.getField(
                oauthFilter,
                "securityContextRepository"
        );
        Object authorizedClientRepository = ReflectionTestUtils.getField(
                oauthFilter,
                "authorizedClientRepository"
        );

        assertInstanceOf(
                RequestAttributeSecurityContextRepository.class,
                contextRepository
        );
        assertSame(
                applicationContext.getBean(NoStoreOAuth2AuthorizedClientRepository.class),
                authorizedClientRepository
        );
    }

    @Test
    void csrfCookieAndHeaderProtectUnsafeRequests() throws Exception {
        Authentication admin = adminAuthentication();
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf")
                        .with(authentication(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andExpect(jsonPath("$.JSESSIONID").doesNotExist())
                .andExpect(jsonPath("$.sessionId").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.idToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").doesNotExist())
                .andReturn();

        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        assertFalse(csrfCookie.isHttpOnly());
        assertFalse(csrfCookie.getSecure());
        assertEquals("/", csrfCookie.getPath());
        assertTrue("lax".equalsIgnoreCase(csrfCookie.getAttribute("SameSite")));

        mockMvc.perform(post("/api/v1/subjects")
                        .with(authentication(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subjectJson("CSRF-MISSING")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/subjects")
                        .with(authentication(admin))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", "invalid-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subjectJson("CSRF-INVALID")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/subjects")
                        .with(authentication(admin))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subjectJson("CSRF-PASS")))
                .andExpect(status().isCreated());
    }

    @Test
    void corsAllowsTheConfiguredFrontendToSendTheCsrfHeaderWithCredentials()
            throws Exception {
        mockMvc.perform(options("/api/v1/subjects")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header(
                                "Access-Control-Request-Headers",
                                "Content-Type, X-XSRF-TOKEN"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://localhost:3000"
                ))
                .andExpect(header().string(
                        "Access-Control-Allow-Credentials",
                        "true"
                ))
                .andExpect(header().string(
                        "Access-Control-Allow-Headers",
                        containsString("X-XSRF-TOKEN")
                ));
    }

    @Test
    void corsDoesNotGrantCredentialsToAnUnconfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/subjects")
                        .header("Origin", "http://localhost:3001")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-XSRF-TOKEN"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void studentCannotCreateGlobalMasterData() throws Exception {
        Authentication student = authenticationFor(ApplicationRole.STUDENT);
        MvcResult meResult = mockMvc.perform(get("/api/auth/me")
                        .with(authentication(student)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = meResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);

        mockMvc.perform(post("/api/v1/subjects")
                        .with(authentication(student))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectCode": "SWE",
                                  "name": "Software Engineering"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
    }

    @Test
    void logoutInvalidatesTheSessionAndNeverExposesTheClientSecret() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Authentication student = authenticationFor(ApplicationRole.STUDENT);
        MvcResult meResult = mockMvc.perform(get("/api/auth/me")
                        .session(session)
                        .with(authentication(student)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = meResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);

        MvcResult result = mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .with(authentication(student))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .param("logout_uri", "https://evil.example"))
                .andExpect(status().isFound())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertTrue(session.isInvalid());
        assertTrue(location != null && location.startsWith("https://cognito.test/logout?"));
        assertTrue(location.contains("client_id=test-client"));
        assertTrue(location.contains(
                "logout_uri=http%3A%2F%2Flocalhost%3A3000%2Flogout%2Fcallback"
        ));
        assertFalse(location.contains("test-secret"));
        assertFalse(location.contains("evil.example"));
        assertEquals(0, result.getResponse().getCookie("JSESSIONID").getMaxAge());
        assertEquals(0, result.getResponse().getCookie("XSRF-TOKEN").getMaxAge());
    }

    @Test
    void logoutRejectsMissingCsrfWithoutInvalidatingTheSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Authentication student = authenticationFor(ApplicationRole.STUDENT);

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .with(authentication(student)))
                .andExpect(status().isForbidden());

        assertFalse(session.isInvalid());
    }

    @Test
    void logoutRejectsInvalidCsrfWithoutInvalidatingTheSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Authentication student = authenticationFor(ApplicationRole.STUDENT);
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf")
                        .session(session)
                        .with(authentication(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .with(authentication(student))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", "invalid-csrf-token"))
                .andExpect(status().isForbidden());

        assertFalse(session.isInvalid());
    }

    @Test
    void anonymousLogoutWithValidCsrfUsesFrameworkLogout() throws Exception {
        Authentication student = authenticationFor(ApplicationRole.STUDENT);
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf")
                        .with(authentication(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("https://cognito.test/logout?")));
    }

    @Test
    void anonymousLogoutWithMissingOrInvalidCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isForbidden());

        Authentication student = authenticationFor(ApplicationRole.STUDENT);
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf")
                        .with(authentication(student)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", "invalid-csrf-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getLogoutDoesNotInvalidateTheSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Authentication student = authenticationFor(ApplicationRole.STUDENT);

        mockMvc.perform(get("/api/auth/logout")
                        .session(session)
                        .with(authentication(student)))
                .andExpect(status().isNotFound());

        assertFalse(session.isInvalid());
    }

    private Authentication adminAuthentication() {
        return authenticationFor(ApplicationRole.ADMIN);
    }

    private Authentication authenticationFor(ApplicationRole role) {
        SagaPrincipal principal = new SagaPrincipal(
                role.name().toLowerCase() + "-subject",
                role.name().toLowerCase() + "@example.com",
                role.name() + " User",
                role,
                UUID.randomUUID(),
                null
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    private String subjectJson(String code) {
        return """
                {
                  "subjectCode": "%s",
                  "name": "CSRF integration subject"
                }
                """.formatted(code);
    }
}
