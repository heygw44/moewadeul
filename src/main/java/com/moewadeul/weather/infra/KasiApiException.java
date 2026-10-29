package com.moewadeul.weather.infra;

import com.moewadeul.common.external.ExternalApiException;

public class KasiApiException extends ExternalApiException {

    public KasiApiException(String message) {
        this(message, null, false);
    }

    public KasiApiException(String message, Throwable cause) {
        this(message, cause, false);
    }

    private KasiApiException(String message, Throwable cause, boolean retryable) {
        super(message, cause, retryable);
    }

    static KasiApiException retryable(String message) {
        return new KasiApiException(message, null, true);
    }

    static KasiApiException retryable(String message, Throwable cause) {
        return new KasiApiException(message, cause, true);
    }
}
