package com.saga.be.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GeneratedOpenApiDocumentationIntegrationTest {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "post", "put", "patch", "delete", "head", "options"
    );

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generatedOperationsHaveVietnameseDocumentationWithoutBearerOrCsrfHeader() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Files.writeString(Path.of("target", "generated-openapi.json"), body);
        JsonNode root = JsonMapper.builder().build().readTree(body);
        Set<String> documentedTags = documentedTagNames(root);
        Set<String> usedTags = new HashSet<>();
        int operationCount = 0;

        Iterator<Map.Entry<String, JsonNode>> paths = root.path("paths").properties().iterator();
        while (paths.hasNext()) {
            Map.Entry<String, JsonNode> path = paths.next();
            Iterator<Map.Entry<String, JsonNode>> methods = path.getValue().properties().iterator();
            while (methods.hasNext()) {
                Map.Entry<String, JsonNode> method = methods.next();
                if (!HTTP_METHODS.contains(method.getKey())) {
                    continue;
                }
                operationCount++;
                JsonNode operation = method.getValue();
                assertTrue(operation.path("summary").isTextual() && !operation.path("summary").asText().isBlank(),
                        () -> "Thiếu summary: " + method.getKey() + " " + path.getKey());
                assertTrue(operation.path("description").isTextual() && !operation.path("description").asText().isBlank(),
                        () -> "Thiếu description: " + method.getKey() + " " + path.getKey());
                assertTrue(operation.path("tags").isArray() && operation.path("tags").size() == 1,
                        () -> "Operation phải có đúng một tag: " + method.getKey() + " " + path.getKey());
                for (JsonNode tag : operation.path("tags")) {
                    usedTags.add(tag.asText());
                    assertTrue(documentedTags.contains(tag.asText()),
                            () -> "Tag chưa có mô tả: " + tag.asText());
                }
                String summary = operation.path("summary").asText();
                assertTrue(summary.matches(".*[À-ỹĐđ].*"),
                        () -> "Summary chưa phải tiếng Việt rõ nghĩa: " + method.getKey() + " " + path.getKey());
                assertFalse(summary.startsWith("Thực hiện tác vụ")
                                || summary.startsWith("Xem dữ liệu")
                                || summary.startsWith("Tạo dữ liệu")
                                || summary.startsWith("Cập nhật dữ liệu")
                                || summary.startsWith("Xóa hoặc ngắt kết nối"),
                        () -> "Summary còn quá generic: " + method.getKey() + " " + path.getKey());
                assertFalse(operation.path("description").asText().startsWith("Thực hiện chức năng"),
                        () -> "Description còn quá generic: " + method.getKey() + " " + path.getKey());
                for (JsonNode parameter : operation.path("parameters")) {
                    assertFalse("Authorization".equalsIgnoreCase(parameter.path("name").asText())
                                    && !path.getKey().startsWith("/api/webhooks/"),
                            () -> "Authorization chỉ được dùng cho webhook provider: " + path.getKey());
                    assertFalse("X-XSRF-TOKEN".equalsIgnoreCase(parameter.path("name").asText()));
                }
            }
        }

        assertEquals(133, operationCount, "Admin dashboard V1 thêm đúng hai API report operation");
        System.out.println("Generated OpenAPI operation count: " + operationCount);
        assertEquals(usedTags, documentedTags, "Global tags phải đúng bằng tập tag thực sự có operation");
        assertEquals(documentedTags.size(), root.path("tags").size(), "Global tags không được trùng tên");
        assertFalse(documentedTags.contains("Notifications"));
        assertTrue(documentedTags.contains("Thông báo"));
        assertFalse(root.path("components").path("securitySchemes").has("bearerAuth"));
        root.path("components").path("securitySchemes").properties().forEach(entry ->
                assertFalse("http".equalsIgnoreCase(entry.getValue().path("type").asText())
                                && "bearer".equalsIgnoreCase(entry.getValue().path("scheme").asText()),
                        () -> "Không được sinh Bearer security scheme: " + entry.getKey()));

        assertTrue(root.path("paths").has("/api/admin/reports/anomalies"));
        assertTrue(root.path("paths").has("/api/admin/reports/graph-processing"));
        assertTrue(root.at("/paths/~1api~1projects~1{projectId}~1github~1issues/get").isObject());
        assertTrue(root.at("/paths/~1api~1projects~1{projectId}~1github~1issues~1{issueId}/get").isObject());
        assertTrue(root.at("/paths/~1api~1v1~1projects~1{projectId}~1tasks~1{taskId}~1github-issues~1{issueId}/post").isObject());
        assertTrue(root.at("/paths/~1api~1v1~1projects~1{projectId}~1tasks~1{taskId}~1github-issues~1{issueId}/delete").isObject());
        assertTrue(root.at("/paths/~1api~1v1~1projects~1{projectId}~1tasks~1{taskId}~1traceability/get").isObject());
        assertTrue(root.at("/paths/~1api~1projects~1{projectId}~1traceability/get").isObject());
        JsonNode issueProperties = root.at(
                "/components/schemas/GitHubIssueSummaryResponse/properties"
        );
        assertFalse(issueProperties.has("githubIssueId"));
        assertFalse(issueProperties.has("nodeId"));
        assertFalse(issueProperties.has("authorExternalId"));
        assertFalse(issueProperties.has("assigneeExternalId"));
        JsonNode teamProjectProperties = root.at(
                "/components/schemas/ProjectSummary/properties"
        );
        assertTrue(teamProjectProperties.has("repositories"));
        JsonNode repositoryReferenceProperties = root.at(
                "/components/schemas/TeamGitHubRepositoryReference/properties"
        );
        assertEquals("integer", repositoryReferenceProperties.path("repositoryId").path("type").asText());
        assertEquals("int64", repositoryReferenceProperties.path("repositoryId").path("format").asText());
        assertTrue(repositoryReferenceProperties.has("repositoryName"));

        Map<String, String> notificationEndpoints = Map.of(
                "/api/me/notifications", "get",
                "/api/me/notifications/unread-count", "get",
                "/api/me/notifications/{notificationId}/read", "patch",
                "/api/me/firebase-installations", "post",
                "/api/me/firebase-installations/{installationId}", "delete",
                "/api/admin/notifications/broadcast", "post",
                "/api/v1/courses/notifications/broadcast", "post"
        );
        notificationEndpoints.forEach((notificationPath, httpMethod) -> {
            JsonNode operation = root.path("paths").path(notificationPath).path(httpMethod);
            assertTrue(operation.isObject(), () -> "Thiếu Notification endpoint: " + httpMethod + " " + notificationPath);
            assertEquals(1, operation.path("tags").size());
            assertEquals("Thông báo", operation.path("tags").get(0).asText());
        });
        assertTrue(root.path("paths").path("/api/me/notifications/{notificationId}/read")
                .path("patch").path("description").asText().contains("CSRF"));
        assertTrue(root.path("paths").path("/api/admin/notifications/broadcast")
                .path("post").path("parameters").toString().contains("Idempotency-Key"));
        assertTrue(root.path("paths").path("/api/v1/courses/notifications/broadcast")
                .path("post").path("parameters").toString().contains("Idempotency-Key"));
        assertEquals("example-browser-fid", root.at("/components/schemas/FirebaseInstallationRegistrationRequest/properties/firebaseInstallationId/example").asText());

        Set<String> jiraMutations = Set.of(
                "post /api/v1/projects/{projectId}/tasks",
                "put /api/v1/projects/{projectId}/tasks/{taskId}",
                "put /api/v1/projects/{projectId}/tasks/{taskId}/assignee",
                "put /api/v1/projects/{projectId}/tasks/{taskId}/sprint",
                "put /api/v1/projects/{projectId}/tasks/{taskId}/estimation",
                "post /api/v1/projects/{projectId}/tasks/{taskId}/transitions",
                "delete /api/v1/projects/{projectId}/tasks/{taskId}",
                "post /api/v1/projects/{projectId}/sprints",
                "put /api/v1/projects/{projectId}/sprints/{sprintId}",
                "post /api/v1/projects/{projectId}/sprints/{sprintId}/start",
                "post /api/v1/projects/{projectId}/sprints/{sprintId}/close",
                "delete /api/v1/projects/{projectId}/sprints/{sprintId}"
        );
        jiraMutations.forEach(mutation -> {
            String httpMethod = mutation.substring(0, mutation.indexOf(' '));
            String jiraPath = mutation.substring(mutation.indexOf(' ') + 1);
            JsonNode operation = root.path("paths").path(jiraPath).path(httpMethod);
            assertTrue(operation.path("parameters").toString().contains("Idempotency-Key"),
                    () -> "Jira mutation thiếu Idempotency-Key: " + httpMethod + " " + jiraPath);
            assertTrue(operation.path("description").asText().contains("CSRF"),
                    () -> "Jira mutation thiếu mô tả CSRF: " + httpMethod + " " + jiraPath);
        });

        Set<String> providerCallbacks = Set.of(
                "/api/integrations/jira/callback",
                "/api/me/integrations/github/callback",
                "/api/integrations/github/setup",
                "/api/integrations/github/project/callback",
                "/api/projects/{projectId}/github/setup",
                "/api/projects/{projectId}/github/callback"
        );
        providerCallbacks.forEach(callbackPath -> assertTrue(
                root.path("paths").path(callbackPath).path("get").path("description").asText().contains("FE không gọi"),
                () -> "Callback chưa ghi rõ FE không gọi thủ công: " + callbackPath));
        assertTrue(root.at("/paths/~1api~1projects~1{projectId}~1github~1repositories~1{repositoryId}~1commits/get/parameters")
                .toString().contains("branch"));
        assertTrue(root.at("/paths/~1api~1v1~1projects~1{projectId}~1sprints/post/parameters")
                .toString().contains("Idempotency-Key"));
        JsonNode teamSprint = root.at("/paths/~1api~1v1~1teams~1{teamId}~1sprints/get");
        assertTrue(teamSprint.at("/responses/200").isObject());
        assertTrue(teamSprint.at("/responses/400").isObject());
        assertTrue(teamSprint.at("/responses/401").isObject());
        assertTrue(teamSprint.at("/responses/403").isObject());
        assertTrue(teamSprint.at("/responses/404").isObject());
        assertTrue(root.at("/components/schemas/SprintListResponse/properties/state").isObject());
        assertTrue(root.at("/components/schemas/SprintSummaryResponse/properties/state").isObject());
        JsonNode taskCreateRequired = root.at("/components/schemas/JiraTaskCreateRequest/required");
        assertTrue(taskCreateRequired.toString().contains("title"));
        assertFalse(taskCreateRequired.toString().contains("issueTypeId"));
        assertFalse(taskCreateRequired.toString().contains("priorityId"));
        assertTrue(root.path("paths").has("/api/admin/users"));
        assertTrue(root.at("/paths/~1api~1admin~1users~1import/post").isObject());
        assertTrue(root.at("/paths/~1api~1admin~1settings~1active-semester/get").isObject());
        assertTrue(root.at("/paths/~1api~1admin~1settings~1active-semester/put").isObject());
        assertTrue(root.path("paths").has("/api/admin/audit-logs"));
        assertTrue(root.path("paths").has("/api/admin/system-stats"));
        assertTrue(root.at("/paths/~1api~1admin~1integrations~1health/get").isObject());
        assertTrue(root.path("paths").has("/api/admin/teams"));
        assertTrue(root.path("paths").has("/api/admin/projects"));
        assertTrue(root.at("/paths/~1api~1admin~1reports~1courses~1{courseId}~1export/get").isObject());
        JsonNode adminStatusPatch = root.at("/paths/~1api~1admin~1users~1{id}~1status/patch");
        assertTrue(adminStatusPatch.isObject());
        assertTrue(adminStatusPatch.toString().contains("AdminUserStatusRequest"));
        JsonNode adminUserProperties = root.at("/components/schemas/AdminUserReadResponse/properties");
        JsonNode adminAuditProperties = root.at("/components/schemas/AdminAuditLogResponse/properties");
        JsonNode adminProjectProperties = root.at("/components/schemas/AdminProjectReadResponse/properties");
        assertTrue(adminUserProperties.has("localProfileId"));
        assertFalse(adminUserProperties.has("cognitoSub"));
        assertFalse(adminAuditProperties.has("oldValues"));
        assertFalse(adminAuditProperties.has("newValues"));
        assertFalse(adminAuditProperties.has("ipAddress"));
        assertFalse(adminProjectProperties.has("repositoryUrl"));
        JsonNode integrationHealthProperties = root.at("/components/schemas/AdminIntegrationHealthResponse/properties");
        assertTrue(integrationHealthProperties.has("jira"));
        assertTrue(integrationHealthProperties.has("gitHub"));
        assertTrue(root.at("/paths/~1api~1v1~1semesters~1{id}/put").isObject());
        assertTrue(root.at("/paths/~1api~1v1~1semesters~1{id}/delete").isObject());
        assertTrue(root.at("/paths/~1api~1v1~1courses~1{id}/put").isObject());
        assertTrue(root.at("/paths/~1api~1v1~1courses~1{id}/delete").isObject());
        assertTrue(root.at("/paths/~1api~1v1~1courses~1{courseId}~1import-students/post").isObject());
        assertTrue(root.at("/paths/~1api~1v1~1courses~1{courseId}~1admin-import-students-template/post").isObject());
        assertTrue(root.at("/paths/~1api~1v1~1courses~1{courseId}~1admin-students-template/get").isObject());
        assertTrue(root.at("/paths/~1api~1v1~1courses~1{courseId}~1students-grouping-template/get").isObject());
        assertTrue(root.at("/paths/~1api~1v1~1courses~1{courseId}~1students~1manual/post").isObject());
        assertTrue(root.at("/paths/~1api~1v1~1courses~1{courseId}~1students~1{studentId}/patch").isObject());
        assertTrue(root.at("/paths/~1api~1v1~1courses~1{courseId}~1students~1{studentId}/delete").isObject());
    }

    private Set<String> documentedTagNames(JsonNode root) {
        Set<String> names = new HashSet<>();
        for (JsonNode tag : root.path("tags")) {
            if (tag.path("name").isTextual() && tag.path("description").isTextual()
                    && !tag.path("description").asText().isBlank()) {
                names.add(tag.path("name").asText());
            }
        }
        return names;
    }
}
