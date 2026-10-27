package com.moewadeul.common.external;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("moewadeul.external-api")
public record ExternalApiProperties(
        @DefaultValue("10") int maxConcurrentCalls,
        @DefaultValue("30s") Duration failureCooldown) {

    public ExternalApiProperties {
        if (maxConcurrentCalls < 1) {
            throw new IllegalArgumentException("moewadeul.external-api.maxConcurrentCalls must be positive");
        }
        if (failureCooldown.isNegative()) {
            throw new IllegalArgumentException("moewadeul.external-api.failureCooldown must not be negative");
        }
    }
}
