package com.moewadeul.member.application;

import com.moewadeul.common.outbox.OutboxEventRecorder;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberSessionCleanup {

    public static final String EVENT_TYPE = "MEMBER_SESSION_CLEANUP";
    public static final String AGGREGATE_TYPE = "MEMBER";

    // 비밀번호 변경이 현재 세션에 적는 바뀐 시각은 응답이 커밋될 때 저장되므로, 그전에 이벤트가 처리되지 않게 미룬다.
    public static final Duration OUTBOX_DELAY = Duration.ofSeconds(30);

    private final OutboxEventRecorder outboxEventRecorder;
    private final MemberSessions memberSessions;

    public void recordInTransaction(long memberId) {
        outboxEventRecorder.recordDeferred(EVENT_TYPE, AGGREGATE_TYPE, memberId, new Payload(memberId), OUTBOX_DELAY);
    }

    public void invalidateAfterCommit(long memberId) {
        try {
            memberSessions.invalidateAll(memberId);
        } catch (RuntimeException e) {
            warnOutboxWillRetry(memberId, e);
        }
    }

    public void invalidateOthersAfterCommit(long memberId, String keptSessionId) {
        try {
            memberSessions.invalidateAllExcept(memberId, keptSessionId);
        } catch (RuntimeException e) {
            warnOutboxWillRetry(memberId, e);
        }
    }

    private static void warnOutboxWillRetry(long memberId, RuntimeException e) {
        log.warn(
                "member session deletion failed, outbox event will retry memberId={} errorType={}",
                memberId,
                e.getClass().getSimpleName());
    }

    public record Payload(long memberId) {}
}
