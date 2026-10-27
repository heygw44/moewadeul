package com.moewadeul.common.testsupport;

import com.moewadeul.common.storage.ImageStorage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class TestImageStorage implements ImageStorage {

    public static final String DELETE_FAILURE_MESSAGE = "simulated storage delete failure";

    private final Map<String, Long> objects = new ConcurrentHashMap<>();
    private final Set<String> failingDeletes = ConcurrentHashMap.newKeySet();
    private final List<String> deletedKeys = new CopyOnWriteArrayList<>();

    @Override
    public PresignedUpload presignUpload(String key, String contentType, long sizeBytes) {
        return new PresignedUpload(
                "https://upload.test.invalid/" + key + "?signed=1",
                Map.of("Content-Type", contentType, "Content-Length", String.valueOf(sizeBytes)),
                Instant.parse("2026-11-20T10:10:00Z"));
    }

    @Override
    public Optional<Long> findObjectSize(String key) {
        return Optional.ofNullable(objects.get(key));
    }

    @Override
    public String presignView(String key) {
        return "https://view.test.invalid/" + key + "?signed=1";
    }

    @Override
    public void delete(String key) {
        if (failingDeletes.contains(key)) {
            throw new IllegalStateException(DELETE_FAILURE_MESSAGE);
        }
        objects.remove(key);
        deletedKeys.add(key);
    }

    public void markUploaded(String key, long sizeBytes) {
        objects.put(key, sizeBytes);
    }

    public void failDeleteOf(String key) {
        failingDeletes.add(key);
    }

    public void allowDeleteOf(String key) {
        failingDeletes.remove(key);
    }

    public List<String> deletedKeys() {
        return List.copyOf(deletedKeys);
    }

    public boolean hasObject(String key) {
        return objects.containsKey(key);
    }

    public void reset() {
        objects.clear();
        failingDeletes.clear();
        deletedKeys.clear();
    }
}
