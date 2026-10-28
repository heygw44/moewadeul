package com.moewadeul.aisearch.infra;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("moewadeul.aisearch.openai")
public record OpenAiProperties(
        @DefaultValue("https://api.openai.com/v1") String baseUrl,
        String apiKey,
        @DefaultValue("gpt-4.1-mini") String model,
        @DefaultValue("1s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout,
        @DefaultValue("300") int maxOutputTokens) {

    private static final String PREFIX = "moewadeul.aisearch.openai.";

    public OpenAiProperties {
        requireNotBlank(baseUrl, "baseUrl");
        requireNotBlank(model, "model");
        apiKey = Objects.requireNonNullElse(apiKey, "");
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException(PREFIX + "maxOutputTokens must be positive");
        }
    }

    private static void requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(PREFIX + name + " must not be blank");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(PREFIX + name + " must be positive");
        }
    }

    @Override
    public String toString() {
        return "OpenAiProperties[baseUrl=" + baseUrl + ", apiKey=****, model=" + model + ", connectTimeout="
                + connectTimeout + ", readTimeout=" + readTimeout + ", maxOutputTokens=" + maxOutputTokens + "]";
    }
}
