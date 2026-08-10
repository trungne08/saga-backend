package com.saga.be.service;

public class FirebaseNotificationDeliveryException extends RuntimeException {

    private final String category;
    private final boolean installationUnusable;

    public FirebaseNotificationDeliveryException(
            String category,
            boolean installationUnusable,
            Throwable cause
    ) {
        super("Firebase notification delivery failed", cause);
        this.category = category;
        this.installationUnusable = installationUnusable;
    }

    public String getCategory() {
        return category;
    }

    public boolean isInstallationUnusable() {
        return installationUnusable;
    }
}
