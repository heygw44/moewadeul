package com.moewadeul.community.application.image;

import com.moewadeul.common.storage.ImageStorage;
import com.moewadeul.community.infra.image.CommunityImageMapper;
import com.moewadeul.community.infra.image.CommunityImageRow;
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
public class CommunityImageCleanupService {

    static final Duration RETENTION = Duration.ofHours(24);

    static final int BATCH_SIZE = 100;

    private final CommunityImageMapper communityImageMapper;
    private final ImageStorage imageStorage;
    private final Clock clock;

    public Result deleteUnattached() {
        Instant createdBefore = clock.instant().minus(RETENTION);
        int deletedRows = 0;
        int storageFailures = 0;
        int batchDeleted;
        int batchRead;
        do {
            List<CommunityImageRow> batch =
                    communityImageMapper.selectUnattachedCreatedBefore(createdBefore, BATCH_SIZE);
            batchRead = batch.size();
            batchDeleted = 0;
            for (CommunityImageRow row : batch) {
                if (communityImageMapper.deleteIfUnattached(row.imageId()) == 0) {
                    continue;
                }
                batchDeleted++;
                if (!deleteObject(row)) {
                    storageFailures++;
                }
            }
            deletedRows += batchDeleted;
        } while (batchRead == BATCH_SIZE && batchDeleted > 0);
        log.info("community image cleanup deletedRows={} storageFailures={}", deletedRows, storageFailures);
        return new Result(deletedRows, storageFailures);
    }

    private boolean deleteObject(CommunityImageRow row) {
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
                    "community image object delete failed imageId={} cause={}",
                    imageId,
                    e.getClass().getSimpleName());
            return false;
        }
    }

    public record Result(int deletedRows, int storageFailures) {}
}
