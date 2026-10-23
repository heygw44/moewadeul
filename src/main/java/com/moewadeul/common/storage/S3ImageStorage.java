package com.moewadeul.common.storage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.external.ExternalApiMetrics;
import com.moewadeul.common.external.ExternalApiProperties;
import com.moewadeul.common.external.ExternalCallLimit;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3 버킷에 커뮤니티 글과 장소 후기의 이미지를 두는 저장소. 자격 증명은 SDK의 기본 방식으로 찾는다. 운영 서버에서는 인스턴스 역할이다.
 *
 * <p>사전 서명 URL은 서버 안에서 계산하므로 S3를 부르지 않는다. 객체 크기 확인과 삭제만 S3를 부르며, SDK는 기본으로 제한 시간이 없어서
 * 호출마다 제한 시간을 걸고 재시도는 한 번만 한다. 로그에는 키나 URL을 남기지 않는다. 키에 회원 ID가 들어 있고, URL에는 서명이 들어 있기 때문이다.
 *
 * <p>객체 크기 확인은 사용자 요청 스레드에서 S3 응답을 기다린다. 그래서 동시에 보내는 확인 수를 제한하고, 한도를 넘거나 S3가 시간 안에
 * 답하지 않으면 503 {@code IMAGE_STORAGE_UNAVAILABLE}로 끝낸다.
 */
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.image-storage",
        name = "storage",
        havingValue = ImageStorageProperties.STORAGE_S3)
public class S3ImageStorage implements ImageStorage, DisposableBean {

    private static final int MAX_ATTEMPTS = 2;
    private static final long VIEW_URL_CACHE_MAX_SIZE = 10_000;
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_LENGTH = "Content-Length";
    private static final String API = "s3";

    private final ImageStorageProperties properties;
    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final ExternalCallLimit headLimit;
    private final Cache<String, String> viewUrls;

    @Autowired
    public S3ImageStorage(
            ImageStorageProperties properties,
            ExternalApiProperties externalApiProperties,
            ExternalApiMetrics metrics) {
        this(
                properties,
                buildClient(properties),
                buildPresigner(properties),
                new ExternalCallLimit(API, externalApiProperties, metrics));
    }

    // 테스트가 고정 자격 증명으로 만든 서명기를 넣을 수 있게 열어 둔다.
    S3ImageStorage(
            ImageStorageProperties properties, S3Client s3Client, S3Presigner presigner, ExternalCallLimit headLimit) {
        this.properties = properties;
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.headLimit = headLimit;
        this.viewUrls = Caffeine.newBuilder()
                .expireAfterWrite(properties.viewUrlSigningInterval())
                .maximumSize(VIEW_URL_CACHE_MAX_SIZE)
                .build();
    }

    private static S3Client buildClient(ImageStorageProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .overrideConfiguration(override -> override.apiCallTimeout(properties.apiCallTimeout())
                        .apiCallAttemptTimeout(properties.apiCallAttemptTimeout())
                        .retryStrategy(retry -> retry.maxAttempts(MAX_ATTEMPTS)))
                .build();
    }

    private static S3Presigner buildPresigner(ImageStorageProperties properties) {
        return S3Presigner.builder().region(Region.of(properties.region())).build();
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType, long sizeBytes) {
        PutObjectRequest object = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .build();
        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(properties.uploadUrlTtl())
                .putObjectRequest(object)
                .build());
        return new PresignedUpload(
                presigned.url().toString(),
                Map.of(CONTENT_TYPE, contentType, CONTENT_LENGTH, String.valueOf(sizeBytes)),
                presigned.expiration());
    }

    @Override
    public Optional<Long> findObjectSize(String key) {
        return headLimit.call(() -> headObject(key), S3ImageStorage::unavailable);
    }

    private Optional<Long> headObject(String key) {
        try {
            return Optional.of(s3Client.headObject(HeadObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(key)
                            .build())
                    .contentLength());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (SdkClientException e) {
            // 시간 초과나 연결 실패다. S3가 오류 코드를 돌려준 경우(권한 없음 등)는 설정 결함이라 그대로 던져 500으로 남긴다.
            BusinessException unavailable = unavailable();
            unavailable.initCause(e);
            throw unavailable;
        }
    }

    private static BusinessException unavailable() {
        return new BusinessException(CommonErrorCode.IMAGE_STORAGE_UNAVAILABLE);
    }

    /**
     * 호출하면 key의 조회 URL을 준다. 서명 시각이 URL에 들어가서 매번 새로 서명하면 URL이 달라지고, 브라우저는 같은 사진을 다시 받는다. 그래서 같은
     * 객체의 URL은 서명 구간 동안 한 번만 서명해 두고 그대로 돌려준다. 구간 끝에 가까워서 받은 URL도 조회 유효 시간만큼은 쓸 수 있도록 서명 유효 시간에
     * 구간 길이를 더한다. 응답에는 같은 시간만큼 브라우저가 캐시하도록 {@code Cache-Control}을 서명에 넣는다.
     */
    @Override
    public String presignView(String key) {
        return viewUrls.get(key, this::signView);
    }

    private String signView(String key) {
        Duration ttl = properties.viewUrlTtl();
        return presigner
                .presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(ttl.plus(properties.viewUrlSigningInterval()))
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(properties.bucket())
                                .key(key)
                                .responseCacheControl("private, max-age=" + ttl.toSeconds())
                                .build())
                        .build())
                .url()
                .toString();
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build());
    }

    @Override
    public void destroy() {
        s3Client.close();
        presigner.close();
    }
}
