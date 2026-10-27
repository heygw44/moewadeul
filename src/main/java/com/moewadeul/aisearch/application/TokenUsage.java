package com.moewadeul.aisearch.application;

public record TokenUsage(int inputTokens, int outputTokens) {

    public TokenUsage {
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
    }
}
