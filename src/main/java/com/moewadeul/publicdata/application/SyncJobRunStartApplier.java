package com.moewadeul.publicdata.application;

import com.moewadeul.common.audit.AdminAuditAction;
import com.moewadeul.common.audit.AdminAuditRecorder;
import com.moewadeul.common.audit.AdminAuditTargetType;
import com.moewadeul.publicdata.application.SyncJobRunService.SyncJobStart;
import com.moewadeul.publicdata.domain.SyncJobRun;
import com.moewadeul.publicdata.domain.SyncJobRunRepository;
import com.moewadeul.publicdata.domain.SyncJobType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 유니크 제약 위반이 이 트랜잭션 안에서 나면 Spring이 롤백 전용으로 표시해서, 안에서 잡아도 커밋할 때 다시 예외가 난다.
// 그래서 SyncJobRunService.begin이 이 트랜잭션 밖에서 예외를 받는다.
@Slf4j
@Component
@RequiredArgsConstructor
class SyncJobRunStartApplier {

    private final SyncJobRunRepository syncJobRunRepository;
    private final AdminAuditRecorder adminAuditRecorder;
    private final Clock clock;

    @Transactional
    Optional<SyncJobStart> start(SyncJobType jobType, Duration staleAfter, Long adminId) {
        Instant now = clock.instant();
        Optional<SyncJobRun> latest = syncJobRunRepository.findFirstByJobTypeOrderByIdDesc(jobType);
        if (latest.isPresent() && latest.get().isRunning()) {
            SyncJobRun running = latest.get();
            if (!running.isStale(now, staleAfter)) {
                log.info("sync job already running jobType={} runId={}", jobType, running.getId());
                return Optional.empty();
            }
            failStaleRun(running, now);
        }
        String resumeCursor = latest.flatMap(SyncJobRun::resumeCursor).orElse(null);
        SyncJobRun started = syncJobRunRepository.save(SyncJobRun.start(jobType, resumeCursor, now));
        if (adminId != null) {
            adminAuditRecorder.record(
                    adminId,
                    AdminAuditAction.SYNC_JOB_RUN,
                    AdminAuditTargetType.SYNC_JOB_RUN,
                    started.getId(),
                    Map.of("jobType", jobType.name()));
        }
        return Optional.of(new SyncJobStart(started.getId(), resumeCursor));
    }

    // DB는 종류마다 RUNNING 기록을 하나만 받는다. 그런데 Hibernate는 새 기록의 INSERT를 바로 보내고 기존 기록의 UPDATE는 커밋할 때 보낸다.
    // 그러면 오래된 기록이 아직 RUNNING인 채로 새 RUNNING 기록이 들어가 제약 위반이 난다. 그래서 오래된 기록을 실패로 바꾼 뒤 바로 DB에 쓴다.
    private void failStaleRun(SyncJobRun running, Instant now) {
        running.fail(SyncJobRunService.STALE_RUN_MESSAGE, now);
        syncJobRunRepository.flush();
        log.warn("stale sync job marked failed jobType={} runId={}", running.getJobType(), running.getId());
    }
}
