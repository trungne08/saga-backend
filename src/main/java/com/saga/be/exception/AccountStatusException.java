package com.saga.be.exception;

import org.springframework.http.HttpStatus;

public class AccountStatusException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AccountStatusException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
