package com.saga.be.service;

public class StudentInvitationDeliveryUnavailableException extends RuntimeException {

    public StudentInvitationDeliveryUnavailableException() {
        super("No production student invitation delivery adapter is configured");
    }
}
