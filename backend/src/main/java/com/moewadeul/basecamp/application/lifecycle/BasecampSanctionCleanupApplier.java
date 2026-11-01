package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.basecamp.domain.BasecampRepository;
import com.moewadeul.basecamp.domain.MemberRemoval;
import com.moewadeul.basecamp.domain.RemovalCause;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class BasecampSanctionCleanupApplier {

    private final BasecampRepository basecampRepository;
    private final OutboxEventRecorder outboxEventRecorder;
    private final Clock clock;

    @Transactional
    MemberRemoval cleanUp(long basecampId, long memberId, RemovalCause cause) {
        // 베이스캠프 행을 쓰기 잠금으로 읽는 조회가 이 트랜잭션의 첫 조회여야 한다. MySQL의 반복 가능한 읽기에서는
        // 잠그지 않는 조회가 먼저 일어나면 그 시점에 읽기 스냅숏이 정해져서, 잠금을 얻은 뒤에도 먼저 끝난 다른 트랜잭션의
        // 커밋이 보이지 않는다. 그러면 이미 빠진 멤버를 다시 처리하거나 남은 멤버를 잘못 알린다.
        Basecamp basecamp = basecampRepository.findByIdForUpdate(basecampId).orElse(null);
        if (basecamp == null) {
            return MemberRemoval.NOTHING;
        }
        MemberRemoval removal = basecamp.removeMember(memberId, cause, clock.instant());
        outboxEventRecorder.recordAll(basecamp.pullEvents());
        return removal;
    }
}
