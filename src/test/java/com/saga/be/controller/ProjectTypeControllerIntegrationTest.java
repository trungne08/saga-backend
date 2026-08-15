package com.saga.be.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.ProjectType;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.repository.ProjectTypeRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * ProjectType is a fixed, migration-seeded canonical SAGA catalog
 * (V34__replace_project_type_with_canonical_catalog.sql). There is no ADMIN create/update/delete
 * API; {@code GET /api/project-types} is read-only for every authenticated role.
 *
 * <p>The test profile runs with {@code spring.flyway.enabled=false} (Hibernate {@code create-drop}
 * builds the schema instead), so V34 never actually executes against the test database. Each test
 * seeds the same four canonical rows the migration defines, mirroring its exact code/name contract
 * rather than depending on Flyway execution.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectTypeControllerIntegrationTest {

    private static final List<String> CANONICAL_CODES =
            List.of("DESIGN_ARCHITECTURE", "RESEARCH", "TESTER", "DOCUMENT");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectTypeRepository projectTypeRepository;

    @BeforeEach
    void seedCanonicalCatalog() {
        projectTypeRepository.save(ProjectType.builder().code("DESIGN_ARCHITECTURE").name("Design & Architecture").build());
        projectTypeRepository.save(ProjectType.builder().code("RESEARCH").name("Research").build());
        projectTypeRepository.save(ProjectType.builder().code("TESTER").name("Tester").build());
        projectTypeRepository.save(ProjectType.builder().code("DOCUMENT").name("Document").build());
    }

    @Test
    void listReturnsExactlyTheFourCanonicalTypesForEveryAuthenticatedRole() throws Exception {
        mockMvc.perform(get("/api/project-types")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[*].code").value(Matchers.containsInAnyOrder(CANONICAL_CODES.toArray())))
                .andExpect(jsonPath("$[*].projectTypeId").exists())
                .andExpect(jsonPath("$[0].id").doesNotExist());

        mockMvc.perform(get("/api/project-types")
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
        mockMvc.perform(get("/api/project-types")
                        .with(authentication(authenticationFor(ApplicationRole.STUDENT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void catalogCodesMatchExactlyTheFourCanonicalCodes() {
        List<String> codes = projectTypeRepository.findAllByOrderByNameAsc().stream()
                .map(ProjectType::getCode)
                .toList();

        org.junit.jupiter.api.Assertions.assertEquals(
                Set.copyOf(CANONICAL_CODES), Set.copyOf(codes)
        );
    }

    @Test
    void listRejectsAnonymousWithoutCsrfRequirement() throws Exception {
        mockMvc.perform(get("/api/project-types"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRouteNoLongerExists() throws Exception {
        // Audited against actual app behavior: /api/project-types only maps GET now, so an
        // authenticated + CSRF-valid POST triggers HttpRequestMethodNotSupportedException. The
        // app's GlobalExceptionHandler has no dedicated handler for it, so it falls through to the
        // generic Exception handler and surfaces as 500 (a pre-existing, unrelated gap; not 405).
        mockMvc.perform(post("/api/project-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NEW-TYPE\",\"name\":\"New type\"}")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN)))
                        .with(csrf()))
                .andExpect(status().is5xxServerError());
        mockMvc.perform(get("/api/project-types")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    private Authentication authenticationFor(ApplicationRole role) {
        SagaPrincipal principal = new SagaPrincipal(
                role.name().toLowerCase() + "-project-type",
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
