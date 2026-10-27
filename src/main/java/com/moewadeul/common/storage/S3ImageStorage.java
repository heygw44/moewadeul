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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

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
                // AWS SDK는 기본으로 API 호출 제한 시간이 없다.
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
            throw unavailable(e);
        } catch (S3Exception e) {
            if (e.statusCode() >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                throw unavailable(e);
            }
            throw e;
        }
    }

    private static BusinessException unavailable(Throwable cause) {
        BusinessException unavailable = unavailable();
        unavailable.initCause(cause);
        return unavailable;
    }

    private static BusinessException unavailable() {
        return new BusinessException(CommonErrorCode.IMAGE_STORAGE_UNAVAILABLE);
    }

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
