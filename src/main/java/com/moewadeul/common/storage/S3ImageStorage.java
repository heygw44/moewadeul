package com.moewadeul.common.storage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.external.ExternalApiMetrics;
import com.moewadeul.common.external.ExternalApiProperties;
import com.moewadeul.common.external.ExternalCallLimit;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Error;
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

    private static final Logger log = LoggerFactory.getLogger(S3ImageStorage.class);
    private static final int MAX_ATTEMPTS = 2;
    private static final int DELETE_CHUNK_SIZE = 1_000;
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
    public Set<String> deleteAll(Collection<String> keys) {
        Set<String> failed = new HashSet<>();
        List<String> distinct = List.copyOf(new LinkedHashSet<>(keys));
        for (int from = 0; from < distinct.size(); from += DELETE_CHUNK_SIZE) {
            int to = Math.min(from + DELETE_CHUNK_SIZE, distinct.size());
            failed.addAll(deleteChunk(distinct.subList(from, to)));
        }
        return failed;
    }

    private Set<String> deleteChunk(List<String> chunk) {
        try {
            List<ObjectIdentifier> objects = chunk.stream()
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList();
            return s3Client
                    .deleteObjects(DeleteObjectsRequest.builder()
                            .bucket(properties.bucket())
                            .delete(Delete.builder()
                                    .objects(objects)
                                    .quiet(true)
                                    .build())
                            .build())
                    .errors()
                    .stream()
                    .map(S3Error::key)
                    .collect(Collectors.toSet());
        } catch (RuntimeException e) {
            log.warn(
                    "image object batch delete failed cause={} chunkSize={}",
                    e.getClass().getSimpleName(),
                    chunk.size());
            return Set.copyOf(chunk);
        }
    }

    @Override
    public void destroy() {
        s3Client.close();
        presigner.close();
    }
}
