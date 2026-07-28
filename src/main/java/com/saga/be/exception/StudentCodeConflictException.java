package com.saga.be.exception;

import java.util.UUID;

public class StudentCodeConflictException extends IdentityConflictException {

    private final String cognitoSub;
    private final UUID localProfileId;

    public StudentCodeConflictException(String cognitoSub, UUID localProfileId) {
        super("Stored student code conflicts with the verified Cognito email");
        this.cognitoSub = cognitoSub;
        this.localProfileId = localProfileId;
    }

    public String getCognitoSub() {
        return cognitoSub;
    }

    public UUID getLocalProfileId() {
        return localProfileId;
    }
}
