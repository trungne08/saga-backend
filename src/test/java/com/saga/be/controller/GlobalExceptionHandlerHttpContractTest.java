package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerHttpContractTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void serializesValidationAndMalformedJsonAsSafeBadRequests() {
        MockHttpServletRequest request = request();

        var validation = handler.constraintViolation(
                new ConstraintViolationException("sensitive constraint", Set.of()), request).getBody();
        var malformedJson = handler.invalidRequest(
                new HttpMessageNotReadableException("Json parse detail", new MockHttpInputMessage(new byte[0])), request).getBody();

        assertEquals(400, validation.status());
        assertEquals("VALIDATION_FAILED", validation.error());
        assertEquals("Request validation failed", validation.message());
        assertEquals(400, malformedJson.status());
        assertEquals("INVALID_REQUEST", malformedJson.error());
        assertEquals("Request is invalid", malformedJson.message());
        assertFalse(malformedJson.message().contains("Json"));
    }

    @Test
    void serializesResponseStatusesWithoutLeakingReasons() {
        MockHttpServletRequest request = request();

        var notFound = handler.responseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "sensitive query"), request).getBody();
        var teamNotFound = handler.responseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "TEAM_NOT_FOUND"), request).getBody();
        var conflict = handler.responseStatus(
                new ResponseStatusException(HttpStatus.CONFLICT, "sensitive constraint"), request).getBody();

        assertEquals("RESOURCE_NOT_FOUND", notFound.error());
        assertEquals("Requested resource was not found", notFound.message());
        assertEquals("TEAM_NOT_FOUND", teamNotFound.error());
        assertEquals("Team not found", teamNotFound.message());
        assertEquals("BUSINESS_CONFLICT", conflict.error());
        assertFalse(conflict.message().contains("constraint"));
    }

    @Test
    void serializesRouteAndUnexpectedErrorsWithoutImplementationDetails() {
        MockHttpServletRequest request = request();

        var route = handler.routeNotFound(
                new NoResourceFoundException(HttpMethod.GET, "/missing", "resource detail"), request).getBody();
        var runtime = handler.internalFailure(
                new IllegalStateException("SQL constraint sensitive detail"), request).getBody();

        assertEquals("ROUTE_NOT_FOUND", route.error());
        assertEquals("INTERNAL_SERVER_ERROR", runtime.error());
        assertEquals("An unexpected error occurred", runtime.message());
        assertFalse(runtime.message().contains("SQL"));
        assertEquals("/api/error-probe", runtime.path());
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/error-probe");
    }
}
