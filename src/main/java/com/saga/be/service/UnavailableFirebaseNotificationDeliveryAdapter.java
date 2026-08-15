package com.saga.be.service;

public class UnavailableFirebaseNotificationDeliveryAdapter
        implements FirebaseNotificationDeliveryAdapter {

    @Override
    public void deliver(FirebaseNotificationMessage message) {
        throw new FirebaseNotificationDeliveryUnavailableException();
    }
}
