package com.moewadeul.publicdata.infra.gocamping;

public class GoCampingApiException extends RuntimeException {

    private final boolean retryable;

    public GoCampingApiException(String message) {
        this(message, null, false);
    }

    GoCampingApiException(String message, Throwable cause) {
        this(message, cause, false);
    }

    private GoCampingApiException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    static GoCampingApiException retryable(String message) {
        return new GoCampingApiException(message, null, true);
    }

    static GoCampingApiException retryable(String message, Throwable cause) {
        return new GoCampingApiException(message, cause, true);
    }

    boolean isRetryable() {
        return retryable;
    }
}
