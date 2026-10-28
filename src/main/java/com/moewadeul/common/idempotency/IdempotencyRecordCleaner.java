package com.moewadeul.common.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyRecordCleaner {

    static final Duration RETENTION = Duration.ofDays(1);

    static final int BATCH_SIZE = 1000;

    private final IdempotencyRecordMapper recordMapper;
    private final Clock clock;

    public int deleteExpired() {
        Instant cutoff = Instant.now(clock).minus(RETENTION);
        int deletedTotal = 0;
        int deleted;
        do {
            deleted = recordMapper.deleteOlderThan(cutoff, BATCH_SIZE);
            deletedTotal += deleted;
        } while (deleted >= BATCH_SIZE);
        log.info("idempotency record deleted count={}", deletedTotal);
        return deletedTotal;
    }
}
