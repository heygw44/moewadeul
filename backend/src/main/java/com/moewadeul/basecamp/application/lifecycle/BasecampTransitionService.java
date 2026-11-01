package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.basecamp.domain.BasecampRepository;
import com.moewadeul.basecamp.domain.CancelReason;
import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BasecampTransitionService {

    private final BasecampRepository basecampRepository;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    @Transactional
    public BasecampStatusResult close(long basecampId, long leaderId) {
        Basecamp basecamp = lockLedBy(basecampId, leaderId);
        basecamp.close(clock.instant());
        return resultOf(basecamp);
    }

    @Transactional
    public BasecampStatusResult reopen(long basecampId, long leaderId) {
        Basecamp basecamp = lockLedBy(basecampId, leaderId);
        basecamp.reopen(clock.instant());
        return resultOf(basecamp);
    }

    @Transactional
    public BasecampStatusResult confirm(long basecampId, long leaderId) {
        Basecamp basecamp = lockLedBy(basecampId, leaderId);
        basecamp.confirm(clock.instant());
        outboxEventRecorder.recordAll(basecamp.pullEvents());
        return resultOf(basecamp);
    }

    @Transactional
    public BasecampStatusResult cancel(long basecampId, long leaderId) {
        Basecamp basecamp = lockLedBy(basecampId, leaderId);
        basecamp.cancel(CancelReason.LEADER, clock.instant());
        outboxEventRecorder.recordAll(basecamp.pullEvents());
        return resultOf(basecamp);
    }

    // 베이스캠프 행의 잠금이 이 트랜잭션의 첫 조회여야 한다. MySQL의 반복 가능한 읽기에서는 잠그지 않는 조회가 먼저 일어나면
    // 그 시점에 읽기 스냅숏이 정해져서, 잠금을 얻은 뒤에도 먼저 끝난 다른 트랜잭션이 커밋한 변경이 보이지 않는다.
    private Basecamp lockLedBy(long basecampId, long leaderId) {
        Basecamp basecamp = basecampRepository.findByIdForUpdate(basecampId).orElseThrow(BusinessException::notFound);
        if (!basecamp.isLeader(leaderId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        return basecamp;
    }

    private static BasecampStatusResult resultOf(Basecamp basecamp) {
        return new BasecampStatusResult(basecamp.getId(), basecamp.getStatus());
    }
}
