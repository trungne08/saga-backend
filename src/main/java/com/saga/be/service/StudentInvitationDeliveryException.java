package com.saga.be.service;

public class StudentInvitationDeliveryException extends RuntimeException {

    private final String category;
    private final String providerExceptionClass;

    public StudentInvitationDeliveryException(
            String category,
            Throwable providerException
    ) {
        super("Student invitation delivery failed", providerException);
        this.category = category;
        this.providerExceptionClass = providerException.getClass().getSimpleName();
    }

    public String getCategory() {
        return category;
    }

    public String getProviderExceptionClass() {
        return providerExceptionClass;
    }
}
