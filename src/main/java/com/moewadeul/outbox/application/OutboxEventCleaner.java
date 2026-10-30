package com.moewadeul.outbox.application;

import com.moewadeul.common.batch.BatchDeletion;
import com.moewadeul.outbox.infra.OutboxEventCleanupMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventCleaner {

    static final Duration RETENTION = Duration.ofDays(7);

    static final Duration FAILED_RETENTION = Duration.ofDays(30);

    static final int BATCH_SIZE = 1000;

    private final OutboxEventCleanupMapper cleanupMapper;
    private final Clock clock;

    public int deleteExpired() {
        Instant now = Instant.now(clock);
        Instant publishedCutoff = now.minus(RETENTION);
        Instant failedCutoff = now.minus(FAILED_RETENTION);
        int publishedDeleted = BatchDeletion.untilDrained(
                BATCH_SIZE, size -> cleanupMapper.deletePublishedBefore(publishedCutoff, size));
        int failedDeleted =
                BatchDeletion.untilDrained(BATCH_SIZE, size -> cleanupMapper.deleteFailedBefore(failedCutoff, size));
        log.info("outbox event deleted published={} failed={}", publishedDeleted, failedDeleted);
        return publishedDeleted + failedDeleted;
    }
}
