package com.moewadeul.trust.application.report;

import com.moewadeul.common.batch.BatchDeletion;
import com.moewadeul.trust.infra.report.ReportContentCleanupMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
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
        int reports =
                BatchDeletion.untilDrained(BATCH_SIZE, size -> cleanupMapper.clearHandledReportContent(cutoff, size));
        int sanctions =
                BatchDeletion.untilDrained(BATCH_SIZE, size -> cleanupMapper.clearFinishedSanctionReason(cutoff, size));
        log.info("report content cleared reportCount={} sanctionCount={}", reports, sanctions);
        return new Result(reports, sanctions);
    }

    public record Result(int reports, int sanctions) {}
}
