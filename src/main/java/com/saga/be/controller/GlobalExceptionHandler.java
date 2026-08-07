package com.saga.be.controller;

import com.saga.be.dto.response.ApiErrorResponse;
import com.saga.be.exception.IdentityConflictException;
import com.saga.be.exception.IdentityServiceException;
import com.saga.be.exception.IntegrationException;
import com.saga.be.exception.InvalidIdentityException;
import com.saga.be.exception.UnauthenticatedRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnauthenticatedRequestException.class)
    public ResponseEntity<ApiErrorResponse> unauthenticated(
            UnauthenticatedRequestException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication is required", request);
    }

    @ExceptionHandler(IdentityConflictException.class)
    public ResponseEntity<ApiErrorResponse> conflict(
            IdentityConflictException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, "BUSINESS_CONFLICT", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidIdentityException.class)
    public ResponseEntity<ApiErrorResponse> invalidIdentity(
            InvalidIdentityException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_IDENTITY", exception.getMessage(), request);
    }

    @ExceptionHandler(IdentityServiceException.class)
    public ResponseEntity<ApiErrorResponse> identityServiceFailure(
            IdentityServiceException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_GATEWAY, "IDENTITY_SERVICE_UNAVAILABLE", exception.getMessage(), request);
    }

    @ExceptionHandler(IntegrationException.class)
    public ResponseEntity<ApiErrorResponse> integrationFailure(
            IntegrationException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(exception.getStatus()).body(
                ApiErrorResponse.of(
                exception.getStatus().value(),
                exception.getCode(),
                exception.getMessage(),
                request.getRequestURI()
            )
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validationFailure(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> constraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<ApiErrorResponse> invalidRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is invalid", request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> responseStatus(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        if (status == HttpStatus.NOT_FOUND && "TEAM_NOT_FOUND".equals(exception.getReason())) {
            return response(status, "TEAM_NOT_FOUND", "Team not found", request);
        }
        return response(status, codeFor(status), messageFor(status), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> routeNotFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND", "Route not found", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> accessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> internalFailure(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String error,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(
                status.value(),
                error,
                message,
                request.getRequestURI()
        ));
    }

    private String codeFor(HttpStatus status) {
        return Map.of(
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                HttpStatus.CONFLICT, "BUSINESS_CONFLICT"
        ).getOrDefault(status, "INTERNAL_SERVER_ERROR");
    }

    private String messageFor(HttpStatus status) {
        return Map.of(
                HttpStatus.BAD_REQUEST, "Request is invalid",
                HttpStatus.UNAUTHORIZED, "Authentication is required",
                HttpStatus.FORBIDDEN, "Access denied",
                HttpStatus.NOT_FOUND, "Requested resource was not found",
                HttpStatus.CONFLICT, "Request conflicts with the current state"
        ).getOrDefault(status, "An unexpected error occurred");
    }
}
