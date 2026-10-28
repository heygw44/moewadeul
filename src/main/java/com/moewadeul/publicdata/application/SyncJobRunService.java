package com.moewadeul.publicdata.application;

import com.moewadeul.common.error.UniqueViolations;
import com.moewadeul.publicdata.domain.SyncJobRun;
import com.moewadeul.publicdata.domain.SyncJobRunRepository;
import com.moewadeul.publicdata.domain.SyncJobStatus;
import com.moewadeul.publicdata.domain.SyncJobType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncJobRunService {

    static final String STALE_RUN_MESSAGE = "실행 기록이 오래 갱신되지 않아, 다음 실행이 작업 도중 서버가 꺼진 것으로 보고 실패로 처리했습니다.";

    private static final String RUNNING_UNIQUE_CONSTRAINT = "uk_sync_job_run_running";
    // 같은 종류를 동시에 시작하면 InnoDB가 같은 키를 넣으려는 트랜잭션 하나를 교착 상태 오류로 중단시키기도 해서 다시 시도한다.
    private static final int MAX_START_ATTEMPTS = 3;

    private final SyncJobRunStartService syncJobRunStartService;
    private final SyncJobRunRepository syncJobRunRepository;
    private final Clock clock;

    public Optional<SyncJobStart> begin(SyncJobType jobType, Duration staleAfter) {
        return begin(jobType, staleAfter, null);
    }

    public Optional<SyncJobStart> begin(SyncJobType jobType, Duration staleAfter, Long adminId) {
        for (int attempt = 1; ; attempt++) {
            try {
                return syncJobRunStartService.start(jobType, staleAfter, adminId);
            } catch (DataIntegrityViolationException e) {
                if (!UniqueViolations.violates(e, RUNNING_UNIQUE_CONSTRAINT)) {
                    throw e;
                }
                log.info("sync job already running jobType={} reason=concurrent start", jobType);
                return Optional.empty();
            } catch (PessimisticLockingFailureException e) {
                if (attempt >= MAX_START_ATTEMPTS) {
                    throw e;
                }
                log.warn("sync job start lost a lock conflict, retrying jobType={} attempt={}", jobType, attempt);
            }
        }
    }

    @Transactional
    public void recordProgress(long runId, String cursor, int processedDelta, int skippedDelta) {
        getRun(runId).recordProgress(cursor, processedDelta, skippedDelta, clock.instant());
    }

    @Transactional
    public void complete(long runId) {
        getRun(runId).complete(clock.instant());
    }

    @Transactional
    public void fail(long runId, String message) {
        getRun(runId).fail(message, clock.instant());
    }

    @Transactional(readOnly = true)
    public boolean hasCompletedSinceStartOf(SyncJobType jobType, long runId) {
        Instant startedAt = getRun(runId).getStartedAt();
        return syncJobRunRepository.existsByJobTypeAndStatusAndFinishedAtAfter(
                jobType, SyncJobStatus.COMPLETED, startedAt);
    }

    private SyncJobRun getRun(long runId) {
        return syncJobRunRepository
                .findById(runId)
                .orElseThrow(() -> new IllegalStateException("실행 기록이 없습니다. runId=" + runId));
    }

    public record SyncJobStart(long runId, String resumeCursor) {}
}
