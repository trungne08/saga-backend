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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@Transactional
class ProjectTypeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectTypeRepository projectTypeRepository;

    @Test
    void listPersistsAndReturnsSortedProjectTypesForEveryAuthenticatedRole() throws Exception {
        projectTypeRepository.save(ProjectType.builder()
                .code(unique("ZETA"))
                .name("Zeta type")
                .description("Zeta description")
                .criteriaConfig("{\"weight\":1}")
                .build());
        projectTypeRepository.save(ProjectType.builder()
                .code(unique("ALPHA"))
                .name("Alpha type")
                .build());

        mockMvc.perform(get("/api/project-types")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alpha type"))
                .andExpect(jsonPath("$[0].code").exists())
                .andExpect(jsonPath("$[0].projectTypeId").exists())
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[1].name").value("Zeta type"))
                .andExpect(jsonPath("$[1].criteriaConfig").value("{\"weight\":1}"));

        mockMvc.perform(get("/api/project-types")
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/project-types")
                        .with(authentication(authenticationFor(ApplicationRole.STUDENT))))
                .andExpect(status().isOk());
    }

    @Test
    void listRejectsAnonymousWithoutCsrfRequirement() throws Exception {
        mockMvc.perform(get("/api/project-types"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsEmptyArrayWhenNoProjectTypeExists() throws Exception {
        mockMvc.perform(get("/api/project-types")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createSucceedsForAdminAndAppearsInSubsequentList() throws Exception {
        mockMvc.perform(post("/api/project-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + unique("RESEARCH") + "\",\"name\":\"Research\","
                                + "\"description\":\"Research project type\",\"criteriaConfig\":\"{\\\"a\\\":1}\"}")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectTypeId").exists())
                .andExpect(jsonPath("$.name").value("Research"))
                .andExpect(jsonPath("$.description").value("Research project type"));

        mockMvc.perform(get("/api/project-types")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Research')]").exists());
    }

    @Test
    void createDuplicateCodeIsControlledConflict() throws Exception {
        String code = unique("TESTER");
        projectTypeRepository.save(ProjectType.builder().code(code).name("Tester").build());

        mockMvc.perform(post("/api/project-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"Tester duplicate\"}")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN)))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void createMissingCodeOrNameReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/project-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Missing code\"}")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN)))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/project-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + unique("NONAME") + "\"}")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN)))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsLecturerStudentAndAnonymous() throws Exception {
        String body = "{\"code\":\"" + unique("DOCUMENT") + "\",\"name\":\"Document\"}";

        mockMvc.perform(post("/api/project-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER)))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/project-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(authentication(authenticationFor(ApplicationRole.STUDENT)))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/project-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/api/project-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + unique("DESIGN") + "\",\"name\":\"Design\"}")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isForbidden());
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

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
