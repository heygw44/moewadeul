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
        // 날씨 경로가 쥘 수 있는 톰캣 스레드는 최대 40개(기다리는 요청 10, 기상청 20, 천문연 10)다.
        // OpenAI·S3까지 함께 느려지면 합이 50개를 넘는다.
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
