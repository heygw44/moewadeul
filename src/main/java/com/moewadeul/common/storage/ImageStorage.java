package com.moewadeul.common.storage;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface ImageStorage {

    PresignedUpload presignUpload(String key, String contentType, long sizeBytes);

    Optional<Long> findObjectSize(String key);

    String presignView(String key);

    void delete(String key);

    record PresignedUpload(String url, Map<String, String> headers, Instant expiresAt) {}
}
