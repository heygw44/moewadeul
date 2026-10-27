package com.moewadeul.common.storage;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("moewadeul.image-storage")
public record ImageStorageProperties(
        @DefaultValue("fake") String storage,
        String bucket,
        @DefaultValue("ap-northeast-2") String region,
        @DefaultValue("10m") Duration uploadUrlTtl,
        @DefaultValue("1h") Duration viewUrlTtl,
        @DefaultValue("30m") Duration viewUrlSigningInterval,
        @DefaultValue("2s") Duration apiCallAttemptTimeout,
        @DefaultValue("5s") Duration apiCallTimeout) {

    public static final String STORAGE_FAKE = "fake";
    public static final String STORAGE_S3 = "s3";

    private static final String PREFIX = "moewadeul.image-storage.";

    // S3는 사전 서명 URL의 유효 시간을 7일까지만 받는다.
    private static final Duration MAX_PRESIGNED_URL_VALIDITY = Duration.ofDays(7);

    public ImageStorageProperties {
        bucket = Objects.requireNonNullElse(bucket, "");
        if (!STORAGE_FAKE.equals(storage) && !STORAGE_S3.equals(storage)) {
            throw new IllegalArgumentException(PREFIX + "storage must be fake or s3");
        }
        if (STORAGE_S3.equals(storage) && bucket.isBlank()) {
            throw new IllegalArgumentException(PREFIX + "bucket must not be blank when storage is s3");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException(PREFIX + "region must not be blank");
        }
        requirePositive(uploadUrlTtl, "uploadUrlTtl");
        requirePositive(viewUrlTtl, "viewUrlTtl");
        requirePositive(viewUrlSigningInterval, "viewUrlSigningInterval");
        if (viewUrlTtl.plus(viewUrlSigningInterval).compareTo(MAX_PRESIGNED_URL_VALIDITY) > 0) {
            throw new IllegalArgumentException(PREFIX + "viewUrlTtl + viewUrlSigningInterval must be at most 7 days");
        }
        requirePositive(apiCallAttemptTimeout, "apiCallAttemptTimeout");
        requirePositive(apiCallTimeout, "apiCallTimeout");
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(PREFIX + name + " must be positive");
        }
    }
}
