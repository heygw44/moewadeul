package com.moewadeul.notification.application.retention;

import com.moewadeul.common.batch.BatchDeletion;
import com.moewadeul.notification.infra.retention.NotificationRetentionMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRetentionService {

    static final Duration READ_RETENTION = Duration.ofDays(90);

    static final int BATCH_SIZE = 1000;

    private final NotificationRetentionMapper retentionMapper;
    private final Clock clock;

    public int deleteExpiredRead() {
        Instant cutoff = Instant.now(clock).minus(READ_RETENTION);
        int deleted = BatchDeletion.untilDrainedByIds(
                BATCH_SIZE,
                size -> retentionMapper.selectExpiredReadIds(cutoff, size),
                ids -> retentionMapper.deleteExpiredRead(ids, cutoff));
        log.info("read notifications deleted count={}", deleted);
        return deleted;
    }
}
