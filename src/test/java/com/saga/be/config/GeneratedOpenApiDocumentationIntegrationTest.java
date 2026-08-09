package com.saga.be.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
                assertTrue(operation.path("tags").isArray() && operation.path("tags").size() > 0,
                        () -> "Thiếu tag: " + method.getKey() + " " + path.getKey());
                for (JsonNode tag : operation.path("tags")) {
                    assertTrue(documentedTags.contains(tag.asText()),
                            () -> "Tag chưa có mô tả: " + tag.asText());
                }
                for (JsonNode parameter : operation.path("parameters")) {
                    assertFalse("Authorization".equalsIgnoreCase(parameter.path("name").asText())
                                    && !path.getKey().startsWith("/api/webhooks/"),
                            () -> "Authorization chỉ được dùng cho webhook provider: " + path.getKey());
                    assertFalse("X-XSRF-TOKEN".equalsIgnoreCase(parameter.path("name").asText()));
                }
            }
        }

        assertTrue(operationCount > 0, "OpenAPI không sinh operation nào");
        System.out.println("Generated OpenAPI operation count: " + operationCount);
        assertFalse(root.path("components").path("securitySchemes").has("bearerAuth"));
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
