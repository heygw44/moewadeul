package com.moewadeul.common.image.application;

import com.moewadeul.common.image.domain.ImageKind;
import com.moewadeul.common.image.infra.AttachableImageMapper;
import com.moewadeul.common.image.infra.UnattachedImageRow;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.common.transaction.OutsideTransaction;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ImageCleanup {

    public static final Duration RETENTION = Duration.ofHours(24);

    public static final int BATCH_SIZE = 100;

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final String keyPrefix;
    private final AttachableImageMapper imageMapper;
    private final ImageStorage imageStorage;
    private final Clock clock;

    protected ImageCleanup(ImageKind kind, AttachableImageMapper imageMapper, ImageStorage imageStorage, Clock clock) {
        this.keyPrefix = kind.keyPrefix();
        this.imageMapper = imageMapper;
        this.imageStorage = imageStorage;
        this.clock = clock;
    }

    public String keyPrefix() {
        return keyPrefix;
    }

    public Result deleteExpiredRowsThenObjects() {
        OutsideTransaction.require("저장소 삭제를 기다리는 동안 DB 커넥션과 행 잠금을 쥐면 안 된다.");
        Instant createdBefore = clock.instant().minus(RETENTION);
        int deletedRows = 0;
        int storageFailures = 0;
        int batchDeleted;
        int batchRead;
        do {
            List<UnattachedImageRow> batch = imageMapper.selectUnattachedCreatedBefore(createdBefore, BATCH_SIZE);
            batchRead = batch.size();
            List<UnattachedImageRow> deleted = new ArrayList<>();
            for (UnattachedImageRow row : batch) {
                if (imageMapper.deleteIfUnattached(row.imageId()) != 0) {
                    deleted.add(row);
                }
            }
            batchDeleted = deleted.size();
            storageFailures += deleteObjectsReturningFailedIds(deleted).size();
            deletedRows += batchDeleted;
        } while (batchRead == BATCH_SIZE && batchDeleted > 0);
        log.info("{} image cleanup deletedRows={} storageFailures={}", keyPrefix, deletedRows, storageFailures);
        return new Result(deletedRows, storageFailures);
    }

    protected Set<Long> deleteObjectsReturningFailedIds(List<UnattachedImageRow> rows) {
        List<String> keys = rows.stream().flatMap(ImageCleanup::keysOf).toList();
        if (keys.isEmpty()) {
            return Set.of();
        }
        Set<String> failedKeys = imageStorage.deleteReturningFailedKeys(keys);
        Set<Long> failedIds = rows.stream()
                .filter(row -> keysOf(row).anyMatch(failedKeys::contains))
                .map(UnattachedImageRow::imageId)
                .collect(Collectors.toSet());
        if (!failedIds.isEmpty()) {
            log.warn("{} image object delete failed images={} keys={}", keyPrefix, failedIds.size(), failedKeys.size());
        }
        return failedIds;
    }

    private static Stream<String> keysOf(UnattachedImageRow row) {
        return row.thumbnailKey() == null ? Stream.of(row.objectKey()) : Stream.of(row.objectKey(), row.thumbnailKey());
    }

    public record Result(int deletedRows, int storageFailures) {}
}
