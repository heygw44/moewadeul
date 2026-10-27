package com.moewadeul.weather.infra;

public class KasiApiException extends RuntimeException {

    private final boolean retryable;

    public KasiApiException(String message) {
        this(message, null, false);
    }

    public KasiApiException(String message, Throwable cause) {
        this(message, cause, false);
    }

    private KasiApiException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    static KasiApiException retryable(String message) {
        return new KasiApiException(message, null, true);
    }

    static KasiApiException retryable(String message, Throwable cause) {
        return new KasiApiException(message, cause, true);
    }

    boolean isRetryable() {
        return retryable;
    }
}
