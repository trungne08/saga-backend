package com.saga.be.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void documentsBrowserSessionWithoutBearerOrPerOperationCsrfHeader() {
        OpenAPI openAPI = new OpenApiConfig().customOpenAPI();

        assertNotNull(openAPI.getComponents().getSecuritySchemes().get("sessionCookie"));
        assertTrue(openAPI.getInfo().getDescription().contains("browser session"));
        assertTrue(openAPI.getTags().stream().allMatch(tag -> tag.getDescription() != null));
        assertFalse(openAPI.getComponents().getSecuritySchemes().containsKey("bearerAuth"));
    }
}
