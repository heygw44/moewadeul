package com.moewadeul.memory.application.image;

import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.memory.infra.image.MemoryImageMapper;
import com.moewadeul.memory.infra.image.MemoryImageRow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryImageCleanupService {

    static final Duration RETENTION = Duration.ofHours(24);

    static final int BATCH_SIZE = 100;

    private final MemoryImageMapper memoryImageMapper;
    private final ImageStorage imageStorage;
    private final Clock clock;

    public Result deleteUnattached() {
        Instant createdBefore = clock.instant().minus(RETENTION);
        Result result =
                deleteInBatches(() -> memoryImageMapper.selectUnattachedCreatedBefore(createdBefore, BATCH_SIZE));
        log.info(
                "memory image cleanup deletedRows={} storageFailures={}",
                result.deletedRows(),
                result.storageFailures());
        return result;
    }

    public Result deleteUnattachedOf(long memberId) {
        int deletedRows = 0;
        int storageFailures = 0;
        long lastImageId = 0;
        List<MemoryImageRow> batch;
        do {
            batch = memoryImageMapper.selectUnattachedByMember(memberId, lastImageId, BATCH_SIZE);
            Result batchResult = deleteObjectsThenRows(batch);
            deletedRows += batchResult.deletedRows();
            storageFailures += batchResult.storageFailures();
            if (!batch.isEmpty()) {
                lastImageId = batch.get(batch.size() - 1).imageId();
            }
        } while (batch.size() == BATCH_SIZE);
        log.info(
                "memory image cleanup memberId={} deletedRows={} storageFailures={}",
                memberId,
                deletedRows,
                storageFailures);
        return new Result(deletedRows, storageFailures);
    }

    private Result deleteObjectsThenRows(List<MemoryImageRow> batch) {
        int deletedRows = 0;
        int storageFailures = 0;
        for (MemoryImageRow row : batch) {
            if (!deleteObject(row)) {
                storageFailures++;
                continue;
            }
            deletedRows += memoryImageMapper.deleteIfUnattached(row.imageId());
        }
        return new Result(deletedRows, storageFailures);
    }

    private Result deleteInBatches(Supplier<List<MemoryImageRow>> nextBatch) {
        int deletedRows = 0;
        int storageFailures = 0;
        int batchDeleted;
        int batchRead;
        do {
            List<MemoryImageRow> batch = nextBatch.get();
            batchRead = batch.size();
            batchDeleted = 0;
            for (MemoryImageRow row : batch) {
                if (memoryImageMapper.deleteIfUnattached(row.imageId()) == 0) {
                    continue;
                }
                batchDeleted++;
                if (!deleteObject(row)) {
                    storageFailures++;
                }
            }
            deletedRows += batchDeleted;
        } while (batchRead == BATCH_SIZE && batchDeleted > 0);
        return new Result(deletedRows, storageFailures);
    }

    private boolean deleteObject(MemoryImageRow row) {
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
                    "memory image object delete failed imageId={} cause={}",
                    imageId,
                    e.getClass().getSimpleName());
            return false;
        }
    }

    public record Result(int deletedRows, int storageFailures) {}
}
