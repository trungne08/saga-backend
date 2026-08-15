package com.saga.be.service;

public class GmailDeliveryException extends RuntimeException {

    private final String category;
    private final boolean retryable;
    private final Integer httpStatus;

    public GmailDeliveryException(
            String category,
            boolean retryable,
            Integer httpStatus,
            Throwable cause
    ) {
        super("Gmail delivery failed", cause);
        this.category = category;
        this.retryable = retryable;
        this.httpStatus = httpStatus;
    }

    public String getCategory() {
        return category;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }
}
