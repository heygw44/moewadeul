package com.moewadeul.member.application;

import com.moewadeul.common.outbox.OutboxEventRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberSessionCleanup {

    public static final String EVENT_TYPE = "MEMBER_SESSION_CLEANUP";
    public static final String AGGREGATE_TYPE = "MEMBER";

    private final OutboxEventRecorder outboxEventRecorder;
    private final MemberSessions memberSessions;

    public void recordInTransaction(long memberId) {
        outboxEventRecorder.record(EVENT_TYPE, AGGREGATE_TYPE, memberId, new Payload(memberId));
    }

    public void invalidateAfterCommit(long memberId) {
        try {
            memberSessions.invalidateAll(memberId);
        } catch (RuntimeException e) {
            log.warn(
                    "member session deletion failed, outbox event will retry memberId={} errorType={}",
                    memberId,
                    e.getClass().getSimpleName());
        }
    }

    public record Payload(long memberId) {}
}
