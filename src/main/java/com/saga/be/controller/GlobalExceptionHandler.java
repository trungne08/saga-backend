package com.saga.be.controller;

import com.saga.be.dto.response.ApiErrorResponse;
import com.saga.be.exception.IdentityConflictException;
import com.saga.be.exception.IdentityServiceException;
import com.saga.be.exception.IntegrationException;
import com.saga.be.exception.InvalidIdentityException;
import com.saga.be.exception.UnauthenticatedRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnauthenticatedRequestException.class)
    public ResponseEntity<ApiErrorResponse> unauthenticated(
            UnauthenticatedRequestException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.UNAUTHORIZED, exception.getMessage(), request);
    }

    @ExceptionHandler(IdentityConflictException.class)
    public ResponseEntity<ApiErrorResponse> conflict(
            IdentityConflictException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidIdentityException.class)
    public ResponseEntity<ApiErrorResponse> invalidIdentity(
            InvalidIdentityException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), request);
    }

    @ExceptionHandler(IdentityServiceException.class)
    public ResponseEntity<ApiErrorResponse> identityServiceFailure(
            IdentityServiceException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_GATEWAY, exception.getMessage(), request);
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

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        ));
    }
}
