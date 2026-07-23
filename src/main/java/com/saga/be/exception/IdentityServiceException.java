package com.saga.be.exception;

public class IdentityServiceException extends RuntimeException {

    public IdentityServiceException(String message) {
        super(message);
    }

    public IdentityServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
