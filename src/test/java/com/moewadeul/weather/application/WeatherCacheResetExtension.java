package com.moewadeul.weather.application;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

public class WeatherCacheResetExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        SpringExtension.getApplicationContext(context)
                .getBean(WeatherCaches.class)
                .clear();
    }
}
