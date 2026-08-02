package com.saga.be.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void documentsFrameworkManagedLogoutWithSessionCsrfAndRedirectResponses() {
        OpenAPI openAPI = new OpenApiConfig().customOpenAPI();
        Operation logout = openAPI.getPaths().get("/api/auth/logout").getPost();

        assertNotNull(logout);
        assertTrue(logout.getDescription().contains("framework-managed"));
        assertTrue(logout.getDescription().contains("JSESSIONID"));
        assertTrue(logout.getDescription().contains("Failed to fetch"));
        assertTrue(logout.getDescription().contains("top-level form POST"));
        assertNotNull(logout.getSecurity());
        assertTrue(logout.getSecurity().stream()
                .anyMatch(requirement -> requirement.containsKey("sessionCookie")));
        Parameter csrfHeader = logout.getParameters().stream()
                .filter(parameter -> "X-XSRF-TOKEN".equals(parameter.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals("header", csrfHeader.getIn());
        assertTrue(csrfHeader.getRequired());
        assertNotNull(logout.getResponses().get("302"));
        assertNotNull(logout.getResponses().get("302").getHeaders().get("Location"));
        assertNotNull(logout.getResponses().get("403"));
        assertFalse(logout.getResponses().containsKey("401"));
        assertFalse(openAPI.getComponents().getSecuritySchemes().containsKey("bearerAuth"));
    }
}
