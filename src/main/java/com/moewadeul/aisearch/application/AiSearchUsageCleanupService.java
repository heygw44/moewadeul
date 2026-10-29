package com.moewadeul.aisearch.application;

import com.moewadeul.aisearch.infra.AiSearchUsageMapper;
import com.moewadeul.common.batch.BatchDeletion;
import com.moewadeul.common.time.KoreaTime;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSearchUsageCleanupService {

    static final int RETENTION_DAYS = 90;

    static final int BATCH_SIZE = 1000;

    private final AiSearchUsageMapper usageMapper;
    private final Clock clock;

    public int deleteExpiredUsage() {
        LocalDate cutoff = KoreaTime.today(clock).minusDays(RETENTION_DAYS);
        int deletedTotal = BatchDeletion.untilDrained(BATCH_SIZE, size -> usageMapper.deleteBefore(cutoff, size));
        log.info("ai search usage deleted count={}", deletedTotal);
        return deletedTotal;
    }
}
