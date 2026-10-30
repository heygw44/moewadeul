package com.moewadeul.common.testsupport;

import com.moewadeul.common.storage.ImageStorage;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class TestImageStorage implements ImageStorage {

    private final Map<String, Long> objects = new ConcurrentHashMap<>();
    private final Set<String> failingDeletes = ConcurrentHashMap.newKeySet();
    private final AtomicInteger batchCalls = new AtomicInteger();
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
    public Set<String> deleteAll(Collection<String> keys) {
        batchCalls.incrementAndGet();
        Set<String> failed = new LinkedHashSet<>();
        for (String key : keys) {
            if (failingDeletes.contains(key)) {
                failed.add(key);
                continue;
            }
            objects.remove(key);
            deletedKeys.add(key);
        }
        return failed;
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

    public int deleteBatchCalls() {
        return batchCalls.get();
    }

    public boolean hasObject(String key) {
        return objects.containsKey(key);
    }

    public void reset() {
        objects.clear();
        failingDeletes.clear();
        deletedKeys.clear();
        batchCalls.set(0);
    }
}
