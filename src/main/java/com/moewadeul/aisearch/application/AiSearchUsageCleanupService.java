package com.moewadeul.aisearch.application;

import com.moewadeul.aisearch.infra.AiSearchUsageMapper;
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
        LocalDate cutoff = AiSearchDay.today(clock).minusDays(RETENTION_DAYS);
        int deletedTotal = 0;
        int deleted;
        do {
            deleted = usageMapper.deleteBefore(cutoff, BATCH_SIZE);
            deletedTotal += deleted;
        } while (deleted >= BATCH_SIZE);
        log.info("ai search usage deleted count={}", deletedTotal);
        return deletedTotal;
    }
}
