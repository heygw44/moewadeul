package com.moewadeul.outbox.application;

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

    static final int BATCH_SIZE = 1000;

    private final OutboxEventCleanupMapper cleanupMapper;
    private final Clock clock;

    public int deletePublished() {
        Instant cutoff = Instant.now(clock).minus(RETENTION);
        int deletedTotal = 0;
        int deleted;
        do {
            deleted = cleanupMapper.deletePublishedBefore(cutoff, BATCH_SIZE);
            deletedTotal += deleted;
        } while (deleted >= BATCH_SIZE);
        log.info("outbox event deleted count={}", deletedTotal);
        return deletedTotal;
    }
}
