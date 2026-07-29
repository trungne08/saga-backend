package com.saga.be.exception;

import org.springframework.http.HttpStatus;

public class IntegrationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public IntegrationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public IntegrationException(
            HttpStatus status,
            String code,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static IntegrationException conflict(String code, String message) {
        return new IntegrationException(HttpStatus.CONFLICT, code, message);
    }

    public static IntegrationException forbidden(String message) {
        return new IntegrationException(
                HttpStatus.FORBIDDEN,
                "INTEGRATION_FORBIDDEN",
                message
        );
    }

    public static IntegrationException invalid(String code, String message) {
        return new IntegrationException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static IntegrationException unavailable(String code) {
        return new IntegrationException(
                HttpStatus.BAD_GATEWAY,
                code,
                "The integration provider is temporarily unavailable"
        );
    }
}
