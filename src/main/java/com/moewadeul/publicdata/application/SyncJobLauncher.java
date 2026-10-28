package com.moewadeul.publicdata.application;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.publicdata.application.SyncJobRunService.SyncJobStart;
import com.moewadeul.publicdata.application.forest.ForestLoadProperties;
import com.moewadeul.publicdata.application.forest.ForestLoadService;
import com.moewadeul.publicdata.application.forest.ForestStandardLoadProperties;
import com.moewadeul.publicdata.application.forest.ForestStandardLoadService;
import com.moewadeul.publicdata.application.gocamping.GoCampingSyncProperties;
import com.moewadeul.publicdata.application.gocamping.GoCampingSyncService;
import com.moewadeul.publicdata.application.park.ParkBoundaryLoadProperties;
import com.moewadeul.publicdata.application.park.ParkBoundaryLoadService;
import com.moewadeul.publicdata.application.rejudge.BakjiRejudgeProperties;
import com.moewadeul.publicdata.application.rejudge.BakjiRejudgeService;
import com.moewadeul.publicdata.domain.PublicDataErrorCode;
import com.moewadeul.publicdata.domain.SyncJobType;
import java.time.Duration;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SyncJobLauncher {

    private static final String REJECTED_MESSAGE = "작업 실행기에 빈 스레드가 없어 작업을 시작하지 못했습니다.";

    private final SyncJobRunService syncJobRunService;
    private final GoCampingSyncService goCampingSyncService;
    private final ForestLoadService forestLoadService;
    private final ForestStandardLoadService forestStandardLoadService;
    private final ParkBoundaryLoadService parkBoundaryLoadService;
    private final GoCampingSyncProperties goCampingSyncProperties;
    private final ForestLoadProperties forestLoadProperties;
    private final ForestStandardLoadProperties forestStandardLoadProperties;
    private final ParkBoundaryLoadProperties parkBoundaryLoadProperties;
    private final BakjiRejudgeService bakjiRejudgeService;
    private final BakjiRejudgeProperties bakjiRejudgeProperties;
    private final ThreadPoolTaskExecutor syncJobExecutor;

    SyncJobLauncher(
            SyncJobRunService syncJobRunService,
            GoCampingSyncService goCampingSyncService,
            ForestLoadService forestLoadService,
            ForestStandardLoadService forestStandardLoadService,
            ParkBoundaryLoadService parkBoundaryLoadService,
            GoCampingSyncProperties goCampingSyncProperties,
            ForestLoadProperties forestLoadProperties,
            ForestStandardLoadProperties forestStandardLoadProperties,
            ParkBoundaryLoadProperties parkBoundaryLoadProperties,
            BakjiRejudgeService bakjiRejudgeService,
            BakjiRejudgeProperties bakjiRejudgeProperties,
            @Qualifier(SyncJobExecutorConfig.SYNC_JOB_EXECUTOR) ThreadPoolTaskExecutor syncJobExecutor) {
        this.syncJobRunService = syncJobRunService;
        this.goCampingSyncService = goCampingSyncService;
        this.forestLoadService = forestLoadService;
        this.forestStandardLoadService = forestStandardLoadService;
        this.parkBoundaryLoadService = parkBoundaryLoadService;
        this.goCampingSyncProperties = goCampingSyncProperties;
        this.forestLoadProperties = forestLoadProperties;
        this.forestStandardLoadProperties = forestStandardLoadProperties;
        this.parkBoundaryLoadProperties = parkBoundaryLoadProperties;
        this.bakjiRejudgeService = bakjiRejudgeService;
        this.bakjiRejudgeProperties = bakjiRejudgeProperties;
        this.syncJobExecutor = syncJobExecutor;
    }

    public long launch(SyncJobType jobType, long adminId) {
        Job job = jobFor(jobType);
        SyncJobStart start = syncJobRunService
                .begin(jobType, job.staleAfter(), adminId)
                .orElseThrow(() -> new BusinessException(PublicDataErrorCode.SYNC_JOB_ALREADY_RUNNING));
        submit(jobType, start, job);
        log.info("sync job launched jobType={} runId={}", jobType, start.runId());
        return start.runId();
    }

    private Job jobFor(SyncJobType jobType) {
        return switch (jobType) {
            case GOCAMPING -> new Job(goCampingSyncProperties.staleAfter(), goCampingSyncService::run);
            case FOREST -> new Job(forestLoadProperties.staleAfter(), forestLoadService::run);
            case FOREST_STANDARD -> new Job(forestStandardLoadProperties.staleAfter(), forestStandardLoadService::run);
            case PARK_BOUNDARY -> new Job(parkBoundaryLoadProperties.staleAfter(), this::loadParkBoundaryThenRejudge);
            case BAKJI_REJUDGE -> new Job(bakjiRejudgeProperties.staleAfter(), bakjiRejudgeService::run);
        };
    }

    private void loadParkBoundaryThenRejudge(SyncJobStart start) {
        parkBoundaryLoadService.run(start);
        if (bakjiRejudgeService.rejudge().isEmpty()) {
            log.info(
                    "bakji rejudge not started because one is running, it repeats itself parkBoundaryRunId={}",
                    start.runId());
        }
    }

    private void submit(SyncJobType jobType, SyncJobStart start, Job job) {
        try {
            syncJobExecutor.execute(() -> runJob(jobType, start, job));
        } catch (TaskRejectedException e) {
            log.warn("sync job rejected jobType={} runId={}", jobType, start.runId());
            syncJobRunService.fail(start.runId(), REJECTED_MESSAGE);
            throw e;
        }
    }

    private static void runJob(SyncJobType jobType, SyncJobStart start, Job job) {
        try {
            job.runner().accept(start);
        } catch (RuntimeException e) {
            log.error("sync job failed jobType={} runId={}", jobType, start.runId(), e);
        }
    }

    private record Job(Duration staleAfter, Consumer<SyncJobStart> runner) {}
}
