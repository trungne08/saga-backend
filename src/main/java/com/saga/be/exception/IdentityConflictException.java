package com.saga.be.exception;

public class IdentityConflictException extends RuntimeException {

    public enum Reason {
        LOCAL_IDENTITY_CONFLICT,
        EMAIL_LINKED_TO_DIFFERENT_COGNITO_SUB
    }

    private final Reason reason;

    public IdentityConflictException(String message) {
        this(message, Reason.LOCAL_IDENTITY_CONFLICT);
    }

    public IdentityConflictException(String message, Reason reason) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
