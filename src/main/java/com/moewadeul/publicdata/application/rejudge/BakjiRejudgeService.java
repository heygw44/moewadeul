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

/**
 * 공원 경계가 바뀐 뒤 모든 박지의 공원 경고를 새 경계로 다시 판정한다.
 *
 * <p>서비스는 박지를 ID 순서로 설정한 수만큼씩 나눠, 장소 모듈이 묶음 하나를 한 트랜잭션에서 판정하고 커밋하게 한다. 그래서 박지가 많아도 묶음 하나만큼만
 * 메모리에 들고, 묶음 하나만큼의 박지 행만 커밋까지 잠근다. 묶음을 커밋할 때마다 실행 기록에 처리 수와 마지막 박지 ID를 남기므로, 관리자는 진행 상황을
 * 보고 다음 실행은 이 기록이 갱신되는지로 작업이 살아 있는지 판단한다.
 *
 * <p>도중에 실패하면 앞 묶음의 판정은 커밋된 채 남는다. 그래도 다음 실행은 남은 진행 위치에서 잇지 않고 처음부터 다시 판정한다. 실패한 뒤 경계를 다시
 * 적재했으면 진행 위치 앞의 박지도 새 경계로 다시 판정해야 하기 때문이다.
 *
 * <p>재판정이 도는 사이에 공원 경계 적재가 끝나면, 그 재판정은 적재 전에 처리한 박지를 옛 경계로 판정한 채 남긴다. 그리고 적재가 이어서 시작하려던
 * 재판정은 이미 도는 재판정 때문에 시작하지 못한다. 그래서 서비스는 재판정을 마칠 때마다 그 실행이 시작된 뒤에 경계 적재가 끝났는지 보고, 끝났으면
 * 새 실행 기록으로 처음부터 다시 판정한다. 그 사이에 다른 재판정이 이미 시작했으면 그 재판정이 자기 시작 시각을 기준으로 같은 확인을 하므로 다시
 * 시작하지 않는다.
 *
 * <p>이 서비스는 트랜잭션을 열지 않는다. 실행 기록과 묶음 판정은 각 서비스의 트랜잭션에서 따로 커밋한다. 그래서 재판정이 실패해도 실패한 실행 기록은 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BakjiRejudgeService {

    private final SyncJobRunService syncJobRunService;
    private final ParkAreaJudgeService parkAreaJudgeService;
    private final BakjiRejudgeProperties properties;
    private final Clock clock;

    /**
     * 호출하면 실행 기록을 시작하고, 박지를 재판정한 결과를 돌려준다. 재판정이 도는 사이에 공원 경계 적재가 끝났으면 처음부터 다시 판정하고 마지막
     * 판정의 결과를 돌려준다. 다른 재판정이 진행 중이라 시작하지 않았으면 빈 값을 돌려준다. 실패하면 실행 기록을 FAILED로 남기고 받은 예외를 그대로
     * 다시 던진다.
     */
    public Optional<BakjiRejudgeResult> rejudge() {
        return syncJobRunService
                .begin(SyncJobType.BAKJI_REJUDGE, properties.staleAfter())
                .map(this::run);
    }

    /**
     * 호출하면 이미 시작한 실행 기록 start로 박지를 재판정하고, 성공하면 실행 기록을 COMPLETED로 바꾼다. 재판정이 도는 사이에 공원 경계 적재가
     * 끝났으면 새 실행 기록을 시작해 처음부터 다시 판정하고, 마지막 판정의 결과를 돌려준다. 첫 실행 기록은 새로 시작하지 않으므로, 호출하는 쪽이
     * {@link SyncJobRunService#begin}으로 BAKJI_REJUDGE 실행을 먼저 시작해 둬야 한다. 실패하면 실행 기록을 FAILED로 남기고 받은 예외를 그대로 다시
     * 던진다.
     */
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

    // 한 실행의 모든 박지가 같은 판정 시각을 갖도록, 시작할 때 한 번 정한 시각을 묶음마다 넘긴다.
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

    // 예외 메시지에는 비밀값과 개인정보가 없다. 그래서 메시지를 그대로 실행 기록에 남긴다.
    // 실패 기록마저 실패하면 원래 예외가 더 중요하므로, 기록 실패는 원래 예외에 덧붙여 함께 던진다.
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
