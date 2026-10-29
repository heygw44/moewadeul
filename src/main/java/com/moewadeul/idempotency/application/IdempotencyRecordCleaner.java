package com.moewadeul.idempotency.application;

import com.moewadeul.common.batch.BatchDeletion;
import com.moewadeul.idempotency.infra.IdempotencyRecordMapper;
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
        int deletedTotal = BatchDeletion.untilDrained(BATCH_SIZE, size -> recordMapper.deleteOlderThan(cutoff, size));
        log.info("idempotency record deleted count={}", deletedTotal);
        return deletedTotal;
    }
}
