package com.moewadeul.common.moderation;

import com.moewadeul.common.batch.BatchDeletion;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ReportContentCleanup {

    public static final Period RETENTION = Period.ofYears(1);

    public static final int BATCH_SIZE = 1000;

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final String target;
    private final Clock clock;

    protected ReportContentCleanup(String target, Clock clock) {
        this.target = target;
        this.clock = clock;
    }

    public String target() {
        return target;
    }

    public int clearExpiredContent() {
        Instant cutoff =
                Instant.now(clock).atZone(ZoneOffset.UTC).minus(RETENTION).toInstant();
        int cleared = BatchDeletion.untilDrained(BATCH_SIZE, size -> clearContentProcessedBefore(cutoff, size));
        log.info("{} report content cleared count={}", target, cleared);
        return cleared;
    }

    protected abstract int clearContentProcessedBefore(Instant cutoff, int limit);
}
