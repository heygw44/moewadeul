package com.moewadeul.publicdata.application.park;

import com.moewadeul.publicdata.application.SyncJob;
import com.moewadeul.publicdata.application.SyncJobRunService.SyncJobStart;
import com.moewadeul.publicdata.application.rejudge.BakjiRejudgeService;
import com.moewadeul.publicdata.domain.SyncJobType;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParkBoundarySyncJob implements SyncJob<ParkBoundaryLoadResult> {

    private final ParkBoundaryLoadService parkBoundaryLoadService;
    private final BakjiRejudgeService bakjiRejudgeService;
    private final ParkBoundaryLoadProperties properties;

    @Override
    public SyncJobType type() {
        return SyncJobType.PARK_BOUNDARY;
    }

    @Override
    public Duration staleAfter() {
        return properties.staleAfter();
    }

    @Override
    public ParkBoundaryLoadResult run(SyncJobStart start) {
        ParkBoundaryLoadResult result = parkBoundaryLoadService.run(start);
        if (bakjiRejudgeService.rejudge().isEmpty()) {
            log.info(
                    "bakji rejudge not started because one is running, it repeats itself parkBoundaryRunId={}",
                    start.runId());
        }
        return result;
    }
}
