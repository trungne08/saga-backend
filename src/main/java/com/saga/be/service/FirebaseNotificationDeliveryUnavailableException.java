package com.saga.be.service;

public class FirebaseNotificationDeliveryUnavailableException extends RuntimeException {
    public FirebaseNotificationDeliveryUnavailableException() {
        super("Firebase notification delivery is unavailable");
    }
}
