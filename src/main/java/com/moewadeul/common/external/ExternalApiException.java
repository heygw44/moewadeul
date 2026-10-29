package com.moewadeul.common.external;

public abstract class ExternalApiException extends RuntimeException {

    private final boolean retryable;

    protected ExternalApiException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
