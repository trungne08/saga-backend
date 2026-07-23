package com.saga.be.exception;

public class UnauthenticatedRequestException extends RuntimeException {

    public UnauthenticatedRequestException() {
        super("Authentication is required");
    }
}
