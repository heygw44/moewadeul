package com.moewadeul.common.storage;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 커뮤니티 글과 장소 후기가 함께 쓰는 이미지 저장소 설정.
 *
 * @param storage 저장소 종류. {@code fake}는 로컬 개발용으로 파일을 저장하지 않고, {@code s3}는 실제 S3 버킷을 쓴다.
 * @param bucket S3 버킷 이름. 비밀값이 아니다. storage가 s3이면 비어 있을 수 없다.
 * @param region 버킷이 있는 AWS 리전
 * @param uploadUrlTtl 업로드 URL을 쓸 수 있는 시간
 * @param viewUrlTtl 조회 URL을 받은 뒤 그 URL을 쓸 수 있는 최소 시간
 * @param viewUrlSigningInterval 조회 URL의 서명 시각을 맞추는 구간. 같은 구간 안에서는 같은 객체에 같은 URL을 준다
 * @param apiCallAttemptTimeout S3 API 호출 한 번(재시도 한 번)을 기다리는 시간
 * @param apiCallTimeout 재시도를 포함해 S3 API 호출 하나가 끝나기를 기다리는 시간
 */
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
