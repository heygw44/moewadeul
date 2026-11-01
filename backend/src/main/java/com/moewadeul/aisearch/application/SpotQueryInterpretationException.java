package com.moewadeul.aisearch.application;

import java.util.Optional;

public class SpotQueryInterpretationException extends RuntimeException {

    private final transient TokenUsage usage;

    public SpotQueryInterpretationException(String message) {
        this(message, null, null);
    }

    public SpotQueryInterpretationException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public SpotQueryInterpretationException(String message, Throwable cause, TokenUsage usage) {
        super(message, cause);
        this.usage = usage;
    }

    public Optional<TokenUsage> usage() {
        return Optional.ofNullable(usage);
    }
}
