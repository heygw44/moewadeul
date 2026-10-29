package com.moewadeul.common.image.application;

import com.moewadeul.common.image.domain.ImageKind;
import com.moewadeul.common.image.infra.AttachableImageMapper;
import com.moewadeul.common.image.infra.UnattachedImageRow;
import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.common.transaction.OutsideTransaction;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
            batchDeleted = 0;
            for (UnattachedImageRow row : batch) {
                if (imageMapper.deleteIfUnattached(row.imageId()) == 0) {
                    continue;
                }
                batchDeleted++;
                if (!deleteObjects(row)) {
                    storageFailures++;
                }
            }
            deletedRows += batchDeleted;
        } while (batchRead == BATCH_SIZE && batchDeleted > 0);
        log.info("{} image cleanup deletedRows={} storageFailures={}", keyPrefix, deletedRows, storageFailures);
        return new Result(deletedRows, storageFailures);
    }

    protected boolean deleteObjects(UnattachedImageRow row) {
        boolean deleted = deleteObject(row.imageId(), row.objectKey());
        if (row.thumbnailKey() == null) {
            return deleted;
        }
        return deleteObject(row.imageId(), row.thumbnailKey()) && deleted;
    }

    private boolean deleteObject(long imageId, String objectKey) {
        try {
            imageStorage.delete(objectKey);
            return true;
        } catch (RuntimeException e) {
            log.warn(
                    "{} image object delete failed imageId={} cause={}",
                    keyPrefix,
                    imageId,
                    e.getClass().getSimpleName());
            return false;
        }
    }

    public record Result(int deletedRows, int storageFailures) {}
}
