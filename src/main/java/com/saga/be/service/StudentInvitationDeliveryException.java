package com.saga.be.service;

public class StudentInvitationDeliveryException extends RuntimeException {

    private final String category;
    private final String providerExceptionClass;
    private final boolean retryable;
    private final Integer httpStatus;

    public StudentInvitationDeliveryException(
            String category,
            Throwable providerException
    ) {
        this(category, false, null, providerException);
    }

    public StudentInvitationDeliveryException(
            String category,
            boolean retryable,
            Integer httpStatus,
            Throwable providerException
    ) {
        super("Student invitation delivery failed", providerException);
        this.category = category;
        this.providerExceptionClass = providerException.getClass().getSimpleName();
        this.retryable = retryable;
        this.httpStatus = httpStatus;
    }

    public String getCategory() {
        return category;
    }

    public String getProviderExceptionClass() {
        return providerExceptionClass;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }
}
