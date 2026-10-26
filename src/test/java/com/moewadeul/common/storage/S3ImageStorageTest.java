package com.moewadeul.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.external.ExternalApiMetrics;
import com.moewadeul.common.external.ExternalApiProperties;
import com.moewadeul.common.external.ExternalCallLimit;
import com.moewadeul.common.storage.ImageStorage.PresignedUpload;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ImageStorageTest {

    private static final String BUCKET = "moewadeul-upload-test";
    private static final String KEY = "community/7/3f0c1c7e-0000-4000-8000-000000000001.jpg";

    private S3Presigner presigner;
    private S3ImageStorage storage;

    @BeforeEach
    void setUp() {
        // 서명 계산에만 쓰는 고정 자격 증명이다. 네트워크를 쓰지 않는다.
        presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access", "test-secret")))
                .build();
        ImageStorageProperties properties = new ImageStorageProperties(
                "s3",
                BUCKET,
                "ap-northeast-2",
                Duration.ofMinutes(10),
                Duration.ofHours(1),
                Duration.ofMinutes(30),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5));
        storage = new S3ImageStorage(
                properties,
                mock(S3Client.class),
                presigner,
                new ExternalCallLimit(
                        "s3",
                        new ExternalApiProperties(10, Duration.ZERO),
                        new ExternalApiMetrics(new SimpleMeterRegistry())));
    }

    @AfterEach
    void tearDown() {
        presigner.close();
    }

    @Test
    @DisplayName("[F-29][CM-06] 업로드 URL은 버킷의 서울 리전 주소이고 content-type과 content-length를 서명 헤더에 넣으며 체크섬 파라미터가 없다")
    void presignUploadSignsContentTypeAndLength() {
        // given
        Instant before = Instant.now();

        // when
        PresignedUpload upload = storage.presignUpload(KEY, "image/jpeg", 2483011L);

        // then
        String url = upload.url();
        assertThat(url).startsWith("https://" + BUCKET + ".s3.ap-northeast-2.amazonaws.com/" + KEY + "?");
        assertThat(url).contains("X-Amz-Signature=");
        assertThat(url)
                .containsIgnoringCase("X-Amz-SignedHeaders=")
                .contains("content-type")
                .contains("content-length");
        assertThat(url.toLowerCase())
                .doesNotContain("x-amz-sdk-checksum-algorithm")
                .doesNotContain("x-amz-checksum");
        assertThat(upload.headers()).isEqualTo(Map.of("Content-Type", "image/jpeg", "Content-Length", "2483011"));
        assertThat(upload.expiresAt())
                .isBetween(
                        before.plus(Duration.ofMinutes(10)).minusSeconds(5),
                        Instant.now().plus(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("[F-29][CM-06] 조회 URL은 서명을 담고 객체 키를 경로로 가리키며, 서명 유효 시간은 조회 유효 시간에 서명 구간을 더한 값이다")
    void presignViewContainsSignature() {
        // when
        String url = storage.presignView(KEY);

        // then
        assertThat(url).startsWith("https://" + BUCKET + ".s3.ap-northeast-2.amazonaws.com/" + KEY + "?");
        assertThat(url).contains("X-Amz-Signature=").contains("X-Amz-Expires=5400");
    }

    @Test
    @DisplayName("[F-29][CM-06] 같은 객체의 조회 URL을 서명 구간 안에 다시 받으면 같은 URL이고, 브라우저가 캐시하도록 Cache-Control이 서명에 들어간다")
    void presignViewReturnsSameUrlWithinInterval() throws InterruptedException {
        // when
        String first = storage.presignView(KEY);
        Thread.sleep(1100);
        String second = storage.presignView(KEY);

        // then
        assertThat(second).isEqualTo(first);
        assertThat(first).contains("response-cache-control=private").contains("max-age%3D3600");
        assertThat(storage.presignView(KEY.replace("0001", "0002"))).isNotEqualTo(first);
    }

    @Test
    @DisplayName("[NFR-05] S3가 시간 안에 답하지 않으면 503 IMAGE_STORAGE_UNAVAILABLE로 끝낸다")
    void findObjectSizeMapsClientTimeoutTo503() {
        // given
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(ApiCallTimeoutException.builder().message("timeout").build());
        S3ImageStorage timingOut = storageWith(s3Client, 10);

        // when & then
        assertThatThrownBy(() -> timingOut.findObjectSize(KEY))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.IMAGE_STORAGE_UNAVAILABLE));
    }

    @Test
    @DisplayName("[NFR-05] 파일이 없으면 빈 값을 돌려준다")
    void findObjectSizeReturnsEmptyWhenMissing() {
        // given
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());

        // when & then
        assertThat(storageWith(s3Client, 10).findObjectSize(KEY)).isEmpty();
    }

    @Test
    @DisplayName("[NFR-05] S3가 5xx로 답하면 500이 아니라 503 IMAGE_STORAGE_UNAVAILABLE로 끝낸다")
    void findObjectSizeMapsServerErrorTo503() {
        // given
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder()
                        .statusCode(503)
                        .message("slow down")
                        .build());

        // when & then
        assertThatThrownBy(() -> storageWith(s3Client, 10).findObjectSize(KEY))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.IMAGE_STORAGE_UNAVAILABLE));
    }

    @Test
    @DisplayName("[NFR-05] S3가 권한 없음 같은 4xx로 답하면 설정 결함이라 S3 예외를 그대로 던진다")
    void findObjectSizeKeepsClientErrorAsIs() {
        // given
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(
                        S3Exception.builder().statusCode(403).message("denied").build());

        // when & then
        assertThatThrownBy(() -> storageWith(s3Client, 10).findObjectSize(KEY)).isInstanceOf(S3Exception.class);
    }

    @Test
    @DisplayName("[NFR-05] 동시에 확인하는 수가 한도에 닿으면 S3를 부르지 않고 503 IMAGE_STORAGE_UNAVAILABLE로 끝낸다")
    void findObjectSizeRejectsWhenLimitReached() throws Exception {
        // given: 확인 하나가 S3 응답을 기다리며 한도(1)의 자리를 쥐고 있다.
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenAnswer(invocation -> {
            entered.countDown();
            release.await(10, TimeUnit.SECONDS);
            return HeadObjectResponse.builder().contentLength(7L).build();
        });
        S3ImageStorage limited = storageWith(s3Client, 1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Optional<Long>> holding = executor.submit(() -> limited.findObjectSize(KEY));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            // when & then
            assertThatThrownBy(() -> limited.findObjectSize(KEY))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.IMAGE_STORAGE_UNAVAILABLE));
            verify(s3Client, times(1)).headObject(any(HeadObjectRequest.class));
            release.countDown();
            assertThat(holding.get(5, TimeUnit.SECONDS)).contains(7L);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private S3ImageStorage storageWith(S3Client s3Client, int maxConcurrentCalls) {
        ImageStorageProperties properties = new ImageStorageProperties(
                "s3",
                BUCKET,
                "ap-northeast-2",
                Duration.ofMinutes(10),
                Duration.ofHours(1),
                Duration.ofMinutes(30),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5));
        return new S3ImageStorage(
                properties,
                s3Client,
                presigner,
                new ExternalCallLimit(
                        "s3",
                        new ExternalApiProperties(maxConcurrentCalls, Duration.ZERO),
                        new ExternalApiMetrics(new SimpleMeterRegistry())));
    }
}
