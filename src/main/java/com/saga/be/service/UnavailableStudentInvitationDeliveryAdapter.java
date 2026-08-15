package com.saga.be.service;

public class UnavailableStudentInvitationDeliveryAdapter
        implements StudentInvitationDeliveryAdapter {

    @Override
    public void deliver(StudentInvitationMessage message) {
        throw new StudentInvitationDeliveryUnavailableException();
    }
}
