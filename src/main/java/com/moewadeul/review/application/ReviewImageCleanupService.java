package com.moewadeul.review.application;

import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.review.infra.ReviewImageMapper;
import com.moewadeul.review.infra.ReviewImageRow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewImageCleanupService {

    static final Duration RETENTION = Duration.ofHours(24);

    static final int BATCH_SIZE = 100;

    private final ReviewImageMapper reviewImageMapper;
    private final ImageStorage imageStorage;
    private final Clock clock;

    public Result deleteUnattached() {
        Instant createdBefore = clock.instant().minus(RETENTION);
        int deletedRows = 0;
        int storageFailures = 0;
        int batchDeleted;
        int batchRead;
        do {
            List<ReviewImageRow> batch = reviewImageMapper.selectUnattachedCreatedBefore(createdBefore, BATCH_SIZE);
            batchRead = batch.size();
            batchDeleted = 0;
            for (ReviewImageRow row : batch) {
                if (reviewImageMapper.deleteIfUnattached(row.imageId()) == 0) {
                    continue;
                }
                batchDeleted++;
                if (!deleteObject(row)) {
                    storageFailures++;
                }
            }
            deletedRows += batchDeleted;
        } while (batchRead == BATCH_SIZE && batchDeleted > 0);
        log.info("review image cleanup deletedRows={} storageFailures={}", deletedRows, storageFailures);
        return new Result(deletedRows, storageFailures);
    }

    private boolean deleteObject(ReviewImageRow row) {
        try {
            imageStorage.delete(row.objectKey());
            return true;
        } catch (RuntimeException e) {
            log.warn(
                    "review image object delete failed imageId={} cause={}",
                    row.imageId(),
                    e.getClass().getSimpleName());
            return false;
        }
    }

    public record Result(int deletedRows, int storageFailures) {}
}
