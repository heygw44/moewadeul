package com.moewadeul.publicdata.infra.gocamping;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("moewadeul.publicdata.gocamping")
public record GoCampingProperties(
        @DefaultValue("https://apis.data.go.kr/B551011/GoCamping")
        String baseUrl,

        String serviceKey,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout,
        @DefaultValue("100") int pageSize) {

    private static final String PREFIX = "moewadeul.publicdata.gocamping.";

    public GoCampingProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(PREFIX + "baseUrl must not be blank");
        }
        serviceKey = Objects.requireNonNullElse(serviceKey, "");
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
        if (pageSize <= 0) {
            throw new IllegalArgumentException(PREFIX + "pageSize must be positive");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(PREFIX + name + " must be positive");
        }
    }

    @Override
    public String toString() {
        return "GoCampingProperties[baseUrl=" + baseUrl + ", serviceKey=****, connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + ", pageSize=" + pageSize + "]";
    }
}
