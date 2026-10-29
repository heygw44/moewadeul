package com.moewadeul.publicdata.application;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.publicdata.application.SyncJobRunService.SyncJobStart;
import com.moewadeul.publicdata.domain.PublicDataErrorCode;
import com.moewadeul.publicdata.domain.SyncJobType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    private final Map<SyncJobType, SyncJob<?>> jobs = new EnumMap<>(SyncJobType.class);
    private final ThreadPoolTaskExecutor syncJobExecutor;

    SyncJobLauncher(
            SyncJobRunService syncJobRunService,
            List<SyncJob<?>> jobs,
            @Qualifier(SyncJobExecutorConfig.SYNC_JOB_EXECUTOR) ThreadPoolTaskExecutor syncJobExecutor) {
        this.syncJobRunService = syncJobRunService;
        this.syncJobExecutor = syncJobExecutor;
        for (SyncJob<?> job : jobs) {
            if (this.jobs.putIfAbsent(job.type(), job) != null) {
                throw new IllegalStateException("같은 종류의 동기화 작업이 둘 이상 등록되었습니다. jobType=" + job.type());
            }
        }
        for (SyncJobType jobType : SyncJobType.values()) {
            if (!this.jobs.containsKey(jobType)) {
                throw new IllegalStateException("동기화 작업이 등록되지 않았습니다. jobType=" + jobType);
            }
        }
    }

    public long launch(SyncJobType jobType, long adminId) {
        SyncJob<?> job = jobs.get(jobType);
        SyncJobStart start = syncJobRunService
                .begin(jobType, job.staleAfter(), adminId)
                .orElseThrow(() -> new BusinessException(PublicDataErrorCode.SYNC_JOB_ALREADY_RUNNING));
        submit(jobType, start, job);
        log.info("sync job launched jobType={} runId={}", jobType, start.runId());
        return start.runId();
    }

    private void submit(SyncJobType jobType, SyncJobStart start, SyncJob<?> job) {
        try {
            syncJobExecutor.execute(() -> runJob(jobType, start, job));
        } catch (TaskRejectedException e) {
            log.warn("sync job rejected jobType={} runId={}", jobType, start.runId());
            syncJobRunService.fail(start.runId(), REJECTED_MESSAGE);
            throw e;
        }
    }

    private static void runJob(SyncJobType jobType, SyncJobStart start, SyncJob<?> job) {
        try {
            job.run(start);
        } catch (RuntimeException e) {
            log.error("sync job failed jobType={} runId={}", jobType, start.runId(), e);
        }
    }
}
