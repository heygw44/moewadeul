package com.moewadeul.weather.application;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * JUnit이 테스트가 끝날 때마다 이 확장을 실행해 날씨 캐시를 비운다.
 * 컨텍스트를 테스트끼리 공유하므로, 앞 테스트가 캐시에 넣은 예보를 다음 테스트가 받으면 WireMock 스텁과 다른 결과가 나오기 때문이다.
 */
public class WeatherCacheResetExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        SpringExtension.getApplicationContext(context)
                .getBean(WeatherCaches.class)
                .clear();
    }
}
