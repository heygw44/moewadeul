package com.moewadeul.weather.infra;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("moewadeul.weather.kma")
public record KmaProperties(
        @DefaultValue("https://apis.data.go.kr/1360000") String baseUrl,
        String serviceKey,
        @DefaultValue("1s") Duration connectTimeout,
        @DefaultValue("2s") Duration readTimeout,
        // 기다리는 요청 10 + 기상청 20 + 천문연 10 = 40이라 톰캣 스레드 50개 중 지도 몫이 남는다.
        @DefaultValue("20") int maxConcurrentCalls) {

    private static final String PREFIX = "moewadeul.weather.kma.";

    public KmaProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(PREFIX + "baseUrl must not be blank");
        }
        serviceKey = Objects.requireNonNullElse(serviceKey, "");
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
        if (maxConcurrentCalls < 1) {
            throw new IllegalArgumentException(PREFIX + "maxConcurrentCalls must be positive");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(PREFIX + name + " must be positive");
        }
    }

    @Override
    public String toString() {
        return "KmaProperties[baseUrl=" + baseUrl + ", serviceKey=****, connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + ", maxConcurrentCalls=" + maxConcurrentCalls + "]";
    }
}
