package com.saga.be;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.dto.request.JiraTaskSprintRequest;
import com.saga.be.dto.request.JiraTaskAssigneeRequest;
import com.saga.be.dto.request.JiraTaskUpdateRequest;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.JiraSprintWriteService;
import com.saga.be.service.JiraTaskWriteService;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.exception.IntegrationException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
class JiraMutationControllerSecurityIntegrationTest {
    private static final UUID PROJECT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RESOURCE_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID RUNTIME_PROJECT_ID = UUID.fromString("38fdf06e-2e31-4c77-894f-369e0c3b210c");
    private static final UUID RUNTIME_TASK_ID = UUID.fromString("d004d4ba-e951-4b1a-bf37-61d3014d85e9");
    private static final UUID RUNTIME_SPRINT_ID = UUID.fromString("daa40782-68dd-4cd0-a173-1458422b4bf6");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private JiraTaskWriteService taskWrites;
    @MockitoBean private JiraSprintWriteService sprintWrites;
    @MockitoBean private JiraProviderClient jiraProvider;

    @BeforeEach
    void resetInteractionEvidence() {
        clearInvocations(taskWrites, sprintWrites, jiraProvider);
    }

    @ParameterizedTest(name = "anonymous {0}")
    @MethodSource("allRoutes")
    void anonymousIsRejectedBeforeWriteService(Route route) throws Exception {
        mockMvc.perform(builder(route).with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(taskWrites, sprintWrites, jiraProvider);
    }

    @ParameterizedTest(name = "missing csrf {0}")
    @MethodSource("mutationRoutes")
    void authenticatedMutationWithoutCsrfIsRejected(Route route) throws Exception {
        mockMvc.perform(builder(route).with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(taskWrites, sprintWrites, jiraProvider);
    }

    @ParameterizedTest(name = "invalid csrf {0}")
    @MethodSource("mutationRoutes")
    void authenticatedMutationWithInvalidCsrfIsRejected(Route route) throws Exception {
        mockMvc.perform(builder(route)
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN)))
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(taskWrites, sprintWrites, jiraProvider);
    }

    @ParameterizedTest(name = "role reaches controller {0} {1}")
    @MethodSource("rolesAndMutationRoutes")
    void supportedApplicationRolesReachController(ApplicationRole role, Route route) throws Exception {
        mockMvc.perform(builder(route)
                        .with(authentication(authenticationFor(role)))
                        .with(csrf()))
                .andExpect(status().is(route.expectedStatus()));
    }

    @org.junit.jupiter.api.Test
    void missingProjectIsMappedTo404WithoutProviderInteraction() throws Exception {
        when(taskWrites.create(any(), eq(PROJECT_ID), any(), any())).thenThrow(new IntegrationException(
                HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "The project does not exist"));
        mockMvc.perform(builder(new Route("task-create", HttpMethod.POST,
                        "/api/v1/projects/" + PROJECT_ID + "/tasks",
                        "{\"title\":\"Task\",\"issueTypeId\":\"3\"}", 201))
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))).with(csrf()))
                .andExpect(status().isNotFound());
        verifyNoInteractions(jiraProvider);
    }

    @org.junit.jupiter.api.Test
    void taskFromAnotherProjectIsMappedTo404WithoutProviderInteraction() throws Exception {
        when(taskWrites.update(any(), eq(PROJECT_ID), eq(RESOURCE_ID), any(), any())).thenThrow(new IntegrationException(
                HttpStatus.NOT_FOUND, "JIRA_RESOURCE_NOT_FOUND", "Task not found"));
        mockMvc.perform(builder(new Route("task-update", HttpMethod.PUT,
                        "/api/v1/projects/" + PROJECT_ID + "/tasks/" + RESOURCE_ID, "{}", 200))
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))).with(csrf()))
                .andExpect(status().isNotFound());
        verifyNoInteractions(jiraProvider);
    }

    @org.junit.jupiter.api.Test
    void sprintFromAnotherProjectIsMappedTo404WithoutProviderInteraction() throws Exception {
        when(sprintWrites.detail(any(), eq(PROJECT_ID), eq(RESOURCE_ID))).thenThrow(new IntegrationException(
                HttpStatus.NOT_FOUND, "JIRA_RESOURCE_NOT_FOUND", "Sprint not found"));
        mockMvc.perform(builder(new Route("sprint-detail", HttpMethod.GET,
                        "/api/v1/projects/" + PROJECT_ID + "/sprints/" + RESOURCE_ID, null, 200))
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isNotFound());
        verifyNoInteractions(jiraProvider);
    }

    @Test
    void sprintIdOnlyJsonDeserializesWithBacklogDefaultingToFalse() throws Exception {
        JiraTaskSprintRequest request = objectMapper.readValue(
                "{\"sprintId\":\"" + RUNTIME_SPRINT_ID + "\"}",
                JiraTaskSprintRequest.class
        );

        assertEquals(RUNTIME_SPRINT_ID, request.sprintId());
        assertFalse(request.backlog());
    }

    @Test
    void taskUpdateBusinessTypeJsonDeserializesAndReachesSessionCsrfProtectedWriteService() throws Exception {
        ArgumentCaptor<JiraTaskUpdateRequest> requestCaptor = ArgumentCaptor.forClass(JiraTaskUpdateRequest.class);

        mockMvc.perform(request(HttpMethod.PUT,
                        "/api/v1/projects/" + RUNTIME_PROJECT_ID + "/tasks/" + RUNTIME_TASK_ID)
                        .header("Idempotency-Key", "integration-test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"REQUEST\"}")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN)))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(taskWrites).update(any(), eq(RUNTIME_PROJECT_ID), eq(RUNTIME_TASK_ID),
                eq("integration-test-key"), requestCaptor.capture());
        assertEquals(TaskType.REQUEST, requestCaptor.getValue().type());
        assertNull(requestCaptor.getValue().priorityId());
        verifyNoInteractions(jiraProvider);
    }

    @Test
    void sprintIdOnlyRequestReachesWriteService() throws Exception {
        ArgumentCaptor<JiraTaskSprintRequest> requestCaptor = ArgumentCaptor.forClass(JiraTaskSprintRequest.class);

        mockMvc.perform(sprintRequest("{\"sprintId\":\"" + RUNTIME_SPRINT_ID + "\"}"))
                .andExpect(status().isOk());

        verify(taskWrites).sprint(any(), eq(RUNTIME_PROJECT_ID), eq(RUNTIME_TASK_ID),
                eq("integration-test-key"), requestCaptor.capture());
        assertEquals(RUNTIME_SPRINT_ID, requestCaptor.getValue().sprintId());
        assertFalse(requestCaptor.getValue().backlog());
        verifyNoInteractions(jiraProvider);
    }

    @Test
    void assigneeIdOnlyJsonDeserializesWithUnassignDefaultingToFalse() throws Exception {
        JiraTaskAssigneeRequest request = objectMapper.readValue(
                "{\"assigneeId\":\"" + RESOURCE_ID + "\"}", JiraTaskAssigneeRequest.class);

        assertEquals(RESOURCE_ID, request.assigneeId());
        assertFalse(request.unassign());
    }

    @Test
    void assigneeIdOnlyRequestReachesWriteService() throws Exception {
        ArgumentCaptor<JiraTaskAssigneeRequest> requestCaptor = ArgumentCaptor.forClass(JiraTaskAssigneeRequest.class);

        mockMvc.perform(request(HttpMethod.PUT,
                        "/api/v1/projects/" + RUNTIME_PROJECT_ID + "/tasks/" + RUNTIME_TASK_ID + "/assignee")
                        .header("Idempotency-Key", "integration-test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":\"" + RESOURCE_ID + "\"}")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN)))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(taskWrites).assign(any(), eq(RUNTIME_PROJECT_ID), eq(RUNTIME_TASK_ID),
                eq("integration-test-key"), requestCaptor.capture());
        assertEquals(RESOURCE_ID, requestCaptor.getValue().assigneeId());
        assertFalse(requestCaptor.getValue().unassign());
        verifyNoInteractions(jiraProvider);
    }

    @Test
    void backlogOnlyRequestReachesWriteService() throws Exception {
        ArgumentCaptor<JiraTaskSprintRequest> requestCaptor = ArgumentCaptor.forClass(JiraTaskSprintRequest.class);

        mockMvc.perform(sprintRequest("{\"backlog\":true}"))
                .andExpect(status().isOk());

        verify(taskWrites).sprint(any(), eq(RUNTIME_PROJECT_ID), eq(RUNTIME_TASK_ID),
                eq("integration-test-key"), requestCaptor.capture());
        assertNull(requestCaptor.getValue().sprintId());
        assertTrue(requestCaptor.getValue().backlog());
        verifyNoInteractions(jiraProvider);
    }

    @Test
    void malformedTaskUuidIsMappedToGenericInvalidRequestBeforeService() throws Exception {
        mockMvc.perform(request(HttpMethod.PUT,
                        "/api/v1/projects/" + RUNTIME_PROJECT_ID + "/tasks/not-a-uuid/sprint")
                        .header("Idempotency-Key", "integration-test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sprintId\":\"" + RUNTIME_SPRINT_ID + "\"}")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));

        verifyNoInteractions(taskWrites, jiraProvider);
    }

    @Test
    void malformedSprintJsonIsMappedToGenericInvalidRequestBeforeService() throws Exception {
        mockMvc.perform(sprintRequest("{\"sprintId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));

        verifyNoInteractions(taskWrites, jiraProvider);
    }

    @Test
    void missingIdempotencyKeyIsMappedToGenericBadRequestBeforeService() throws Exception {
        mockMvc.perform(request(HttpMethod.PUT,
                        "/api/v1/projects/" + RUNTIME_PROJECT_ID + "/tasks/" + RUNTIME_TASK_ID + "/sprint")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sprintId\":\"" + RUNTIME_SPRINT_ID + "\"}")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));

        verifyNoInteractions(taskWrites, jiraProvider);
    }

    @Test
    void missingCsrfRejectsSprintRequestBeforeService() throws Exception {
        mockMvc.perform(request(HttpMethod.PUT,
                        "/api/v1/projects/" + RUNTIME_PROJECT_ID + "/tasks/" + RUNTIME_TASK_ID + "/sprint")
                        .header("Idempotency-Key", "integration-test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sprintId\":\"" + RUNTIME_SPRINT_ID + "\"}")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(taskWrites, jiraProvider);
    }

    private MockHttpServletRequestBuilder sprintRequest(String body) {
        return request(HttpMethod.PUT,
                "/api/v1/projects/" + RUNTIME_PROJECT_ID + "/tasks/" + RUNTIME_TASK_ID + "/sprint")
                .header("Idempotency-Key", "integration-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(authentication(authenticationFor(ApplicationRole.ADMIN)))
                .with(csrf());
    }

    private static Stream<Route> allRoutes() {
        return Stream.concat(Stream.of(new Route("sprint-detail", HttpMethod.GET,
                "/api/v1/projects/" + PROJECT_ID + "/sprints/" + RESOURCE_ID, null, 200)), mutationRoutes());
    }

    private static Stream<Arguments> rolesAndMutationRoutes() {
        return Stream.of(ApplicationRole.ADMIN, ApplicationRole.LECTURER, ApplicationRole.STUDENT)
                .flatMap(role -> mutationRoutes().map(route -> Arguments.of(role, route)));
    }

    private static Stream<Route> mutationRoutes() {
        return Stream.of(
                new Route("task-create", HttpMethod.POST, "/api/v1/projects/" + PROJECT_ID + "/tasks", "{\"title\":\"Task\",\"issueTypeId\":\"3\"}", 201),
                new Route("task-update", HttpMethod.PUT, "/api/v1/projects/" + PROJECT_ID + "/tasks/" + RESOURCE_ID, "{}", 200),
                new Route("task-delete", HttpMethod.DELETE, "/api/v1/projects/" + PROJECT_ID + "/tasks/" + RESOURCE_ID, null, 204),
                new Route("task-transition", HttpMethod.POST, "/api/v1/projects/" + PROJECT_ID + "/tasks/" + RESOURCE_ID + "/transitions", "{\"transitionId\":\"31\"}", 200),
                new Route("task-assignee", HttpMethod.PUT, "/api/v1/projects/" + PROJECT_ID + "/tasks/" + RESOURCE_ID + "/assignee", "{\"unassign\":true}", 200),
                new Route("task-sprint", HttpMethod.PUT, "/api/v1/projects/" + PROJECT_ID + "/tasks/" + RESOURCE_ID + "/sprint", "{\"backlog\":true}", 200),
                new Route("task-estimation", HttpMethod.PUT, "/api/v1/projects/" + PROJECT_ID + "/tasks/" + RESOURCE_ID + "/estimation", "{\"value\":3}", 200),
                new Route("sprint-create", HttpMethod.POST, "/api/v1/projects/" + PROJECT_ID + "/sprints", "{\"name\":\"Sprint\"}", 201),
                new Route("sprint-update", HttpMethod.PUT, "/api/v1/projects/" + PROJECT_ID + "/sprints/" + RESOURCE_ID, "{}", 200),
                new Route("sprint-delete", HttpMethod.DELETE, "/api/v1/projects/" + PROJECT_ID + "/sprints/" + RESOURCE_ID, null, 204),
                new Route("sprint-start", HttpMethod.POST, "/api/v1/projects/" + PROJECT_ID + "/sprints/" + RESOURCE_ID + "/start", null, 200),
                new Route("sprint-close", HttpMethod.POST, "/api/v1/projects/" + PROJECT_ID + "/sprints/" + RESOURCE_ID + "/close", null, 200)
        );
    }

    private MockHttpServletRequestBuilder builder(Route route) {
        MockHttpServletRequestBuilder builder = request(route.method(), route.url())
                .header("Idempotency-Key", "integration-test-key");
        if (route.body() != null) builder.contentType(MediaType.APPLICATION_JSON).content(route.body());
        return builder;
    }

    private Authentication authenticationFor(ApplicationRole role) {
        SagaPrincipal principal = new SagaPrincipal(role.name().toLowerCase() + "-subject",
                role.name().toLowerCase() + "@example.test", role.name(), role, UUID.randomUUID(), AccountStatus.ACTIVE);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private record Route(String name, HttpMethod method, String url, String body, int expectedStatus) {
        @Override public String toString() { return name; }
    }
}
