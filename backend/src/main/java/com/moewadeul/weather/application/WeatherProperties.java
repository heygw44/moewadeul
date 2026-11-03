package com.moewadeul.weather.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("moewadeul.weather")
public record WeatherProperties(
        @DefaultValue("3s") Duration responseDeadline,
        @DefaultValue("500") int maxInFlight) {

    private static final String PREFIX = "moewadeul.weather.";

    public WeatherProperties {
        if (responseDeadline.isZero() || responseDeadline.isNegative()) {
            throw new IllegalArgumentException(PREFIX + "responseDeadline must be positive");
        }
        if (maxInFlight < 1) {
            throw new IllegalArgumentException(PREFIX + "maxInFlight must be positive");
        }
    }
}
