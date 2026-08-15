package com.saga.be.service;

public class UnavailableWarningEmailDeliveryAdapter implements WarningEmailDeliveryAdapter {

    @Override
    public void deliver(GmailMessage message) {
        throw new GmailDeliveryException(
                "DELIVERY_UNAVAILABLE",
                false,
                null,
                new IllegalStateException("Gmail warning transport is not configured")
        );
    }
}
