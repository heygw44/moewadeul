package com.moewadeul.common.storage;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ImageStorage {

    PresignedUpload presignUpload(String key, String contentType, long sizeBytes);

    Optional<Long> findObjectSize(String key);

    String presignView(String key);

    Set<String> deleteReturningFailedKeys(Collection<String> keys);

    record PresignedUpload(String url, Map<String, String> headers, Instant expiresAt) {}
}
