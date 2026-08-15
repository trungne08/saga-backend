package com.saga.be.exception;

import org.springframework.http.HttpStatus;

/** Safe, deterministic failures for the existing Course XLSX import route. */
public class CourseImportException extends IdentityConflictException {

    private final HttpStatus status;
    private final String errorCode;

    public CourseImportException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
