package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.basecamp.domain.BasecampStatus;
import com.moewadeul.basecamp.infra.lifecycle.BasecampAutoTransitionMapper;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class BasecampAutoTransitionApplier {

    private final BasecampAutoTransitionMapper autoTransitionMapper;
    private final OutboxEventRecorder outboxEventRecorder;

    @Transactional
    boolean processDayBeforeDeparture(long basecampId, Instant now) {
        // 베이스캠프 행을 쓰기 잠금으로 읽는 조회가 이 트랜잭션의 첫 조회여야 한다. MySQL의 반복 가능한 읽기에서는
        // 잠그지 않는 조회가 먼저 일어나면 그 시점에 읽기 스냅숏이 정해져서, 잠금을 얻은 뒤에도 먼저 끝난 탈퇴 같은
        // 다른 트랜잭션의 커밋이 보이지 않는다. 그러면 이미 빠진 멤버를 세어 인원을 잘못 판단한다.
        String status = autoTransitionMapper.selectStatusForUpdate(basecampId);
        if (!isBeforeConfirm(status)) {
            return false;
        }
        boolean enough = autoTransitionMapper.countActiveMembers(basecampId) >= Basecamp.MIN_CONFIRM_HEADCOUNT;
        int changed = enough
                ? autoTransitionMapper.confirmIfBeforeConfirm(basecampId, now)
                : autoTransitionMapper.cancelForNotEnoughMembers(basecampId, now);
        if (changed == 0) {
            return false;
        }
        autoTransitionMapper.expirePendingApplications(basecampId, now);
        String eventType =
                enough ? BasecampTransitionEvents.CONFIRMED_EVENT_TYPE : BasecampTransitionEvents.CANCELED_EVENT_TYPE;
        recordTransition(eventType, basecampId);
        return true;
    }

    @Transactional
    boolean complete(long basecampId, LocalDate today, Instant now) {
        if (autoTransitionMapper.completeIfConfirmed(basecampId, today, now) == 0) {
            return false;
        }
        autoTransitionMapper.expirePendingApplications(basecampId, now);
        recordTransition(BasecampTransitionEvents.COMPLETED_EVENT_TYPE, basecampId);
        return true;
    }

    private static boolean isBeforeConfirm(String status) {
        return BasecampStatus.RECRUITING.name().equals(status)
                || BasecampStatus.CLOSED.name().equals(status);
    }

    private void recordTransition(String eventType, long basecampId) {
        List<Long> memberIds = autoTransitionMapper.selectActiveMemberIds(basecampId);
        outboxEventRecorder.record(
                eventType,
                BasecampTransitionEvents.AGGREGATE_TYPE,
                basecampId,
                new BasecampTransitionEvents.Payload(basecampId, memberIds));
    }
}
