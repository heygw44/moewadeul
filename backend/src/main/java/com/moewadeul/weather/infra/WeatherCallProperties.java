package com.moewadeul.weather.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 날씨는 가상 스레드에서 기다려 Tomcat 스레드와 상관없으므로, 스레드를 보호하는 공통 한도와 따로 둔다.
@ConfigurationProperties("moewadeul.weather")
public record WeatherCallProperties(@DefaultValue("25") int maxConcurrentCalls) {

    public WeatherCallProperties {
        if (maxConcurrentCalls < 1) {
            throw new IllegalArgumentException("moewadeul.weather.maxConcurrentCalls must be positive");
        }
    }
}
