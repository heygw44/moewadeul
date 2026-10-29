package com.moewadeul.weather.infra;

import com.moewadeul.common.external.ExternalApiException;

public class KmaApiException extends ExternalApiException {

    public KmaApiException(String message) {
        this(message, null, false);
    }

    public KmaApiException(String message, Throwable cause) {
        this(message, cause, false);
    }

    private KmaApiException(String message, Throwable cause, boolean retryable) {
        super(message, cause, retryable);
    }

    static KmaApiException retryable(String message) {
        return new KmaApiException(message, null, true);
    }

    static KmaApiException retryable(String message, Throwable cause) {
        return new KmaApiException(message, cause, true);
    }
}
