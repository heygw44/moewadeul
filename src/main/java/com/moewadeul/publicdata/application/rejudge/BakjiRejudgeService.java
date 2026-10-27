package com.moewadeul.publicdata.application.rejudge;

import com.moewadeul.publicdata.application.SyncJobRunService;
import com.moewadeul.publicdata.application.SyncJobRunService.SyncJobStart;
import com.moewadeul.publicdata.domain.SyncJobType;
import com.moewadeul.spot.application.park.BakjiRejudgeChunk;
import com.moewadeul.spot.application.park.BakjiRejudgeCounts;
import com.moewadeul.spot.application.park.ParkAreaJudgeService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BakjiRejudgeService {

    private final SyncJobRunService syncJobRunService;
    private final ParkAreaJudgeService parkAreaJudgeService;
    private final BakjiRejudgeProperties properties;
    private final Clock clock;

    public Optional<BakjiRejudgeResult> rejudge() {
        return syncJobRunService
                .begin(SyncJobType.BAKJI_REJUDGE, properties.staleAfter())
                .map(this::run);
    }

    public BakjiRejudgeResult run(SyncJobStart start) {
        SyncJobStart current = start;
        while (true) {
            BakjiRejudgeResult result = runOnce(current.runId());
            Optional<SyncJobStart> next = beginAgainIfBoundaryReloaded(current.runId());
            if (next.isEmpty()) {
                return result;
            }
            current = next.get();
        }
    }

    private Optional<SyncJobStart> beginAgainIfBoundaryReloaded(long finishedRunId) {
        if (!syncJobRunService.hasCompletedSinceStartOf(SyncJobType.PARK_BOUNDARY, finishedRunId)) {
            return Optional.empty();
        }
        Optional<SyncJobStart> next = syncJobRunService.begin(SyncJobType.BAKJI_REJUDGE, properties.staleAfter());
        log.info(
                "bakji rejudge repeats because park boundary was loaded while it ran runId={} repeated={}",
                finishedRunId,
                next.isPresent());
        return next;
    }

    private BakjiRejudgeResult runOnce(long runId) {
        try {
            log.info("bakji rejudge started runId={}", runId);
            BakjiRejudgeCounts counts = rejudgeAll(runId);
            BakjiRejudgeResult result = new BakjiRejudgeResult(counts.judged(), counts.newlyWarned(), counts.cleared());
            syncJobRunService.complete(runId);
            log.info(
                    "bakji rejudge completed runId={} judged={} newlyWarned={} cleared={}",
                    runId,
                    result.judgedCount(),
                    result.newlyWarnedCount(),
                    result.clearedCount());
            return result;
        } catch (RuntimeException e) {
            recordFailure(runId, e);
            throw e;
        }
    }

    private BakjiRejudgeCounts rejudgeAll(long runId) {
        Instant now = clock.instant();
        BakjiRejudgeCounts total = new BakjiRejudgeCounts(0, 0, 0);
        long afterId = 0;
        while (true) {
            BakjiRejudgeChunk chunk = parkAreaJudgeService.rejudgeBakjiChunk(afterId, properties.chunkSize(), now);
            if (chunk.lastId() == null) {
                return total;
            }
            total = total.plus(chunk.counts());
            afterId = chunk.lastId();
            syncJobRunService.recordProgress(
                    runId, String.valueOf(afterId), chunk.counts().judged(), 0);
        }
    }

    private void recordFailure(long runId, RuntimeException cause) {
        log.warn(
                "bakji rejudge failed runId={} error={}",
                runId,
                cause.getClass().getSimpleName());
        try {
            syncJobRunService.fail(runId, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (RuntimeException failure) {
            cause.addSuppressed(failure);
        }
    }
}
