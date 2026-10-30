package com.moewadeul.common.storage;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "moewadeul.image-storage",
        name = "storage",
        havingValue = ImageStorageProperties.STORAGE_FAKE,
        matchIfMissing = true)
public class LocalFakeImageStorage implements ImageStorage {

    private static final String UPLOAD_URL_PREFIX = "http://localhost/local-fake-upload/";
    private static final String VIEW_URL_PREFIX = "http://localhost/local-fake-view/";

    private final ImageStorageProperties properties;
    private final Clock clock;
    private final Map<String, Long> sizes = new ConcurrentHashMap<>();

    @Override
    public PresignedUpload presignUpload(String key, String contentType, long sizeBytes) {
        sizes.put(key, sizeBytes);
        return new PresignedUpload(
                UPLOAD_URL_PREFIX + key,
                Map.of("Content-Type", contentType, "Content-Length", String.valueOf(sizeBytes)),
                clock.instant().plus(properties.uploadUrlTtl()));
    }

    @Override
    public Optional<Long> findObjectSize(String key) {
        return Optional.ofNullable(sizes.get(key));
    }

    @Override
    public String presignView(String key) {
        return VIEW_URL_PREFIX + key;
    }

    @Override
    public Set<String> deleteAll(Collection<String> keys) {
        keys.forEach(sizes::remove);
        return Set.of();
    }
}
