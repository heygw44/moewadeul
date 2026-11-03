package com.moewadeul.weather.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("moewadeul.weather")
public record WeatherCallProperties(@DefaultValue("25") int maxConcurrentCalls) {

    public WeatherCallProperties {
        if (maxConcurrentCalls < 1) {
            throw new IllegalArgumentException("moewadeul.weather.maxConcurrentCalls must be positive");
        }
    }
}
