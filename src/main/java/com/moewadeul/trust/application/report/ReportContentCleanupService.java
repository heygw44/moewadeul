package com.moewadeul.trust.application.report;

import com.moewadeul.trust.infra.report.ReportContentCleanupMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportContentCleanupService {

    static final Period RETENTION = Period.ofYears(1);

    static final int BATCH_SIZE = 1000;

    private final ReportContentCleanupMapper cleanupMapper;
    private final Clock clock;

    public Result clearExpiredContent() {
        Instant cutoff =
                Instant.now(clock).atZone(ZoneOffset.UTC).minus(RETENTION).toInstant();
        int reports = repeatUntilDone(cutoff, cleanupMapper::clearHandledReportContent);
        int sanctions = repeatUntilDone(cutoff, cleanupMapper::clearFinishedSanctionReason);
        log.info("report content cleared reportCount={} sanctionCount={}", reports, sanctions);
        return new Result(reports, sanctions);
    }

    private static int repeatUntilDone(Instant cutoff, BiFunction<Instant, Integer, Integer> clearBatch) {
        int total = 0;
        int cleared;
        do {
            cleared = clearBatch.apply(cutoff, BATCH_SIZE);
            total += cleared;
        } while (cleared >= BATCH_SIZE);
        return total;
    }

    public record Result(int reports, int sanctions) {}
}
