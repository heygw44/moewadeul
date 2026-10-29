package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.basecamp.domain.Basecamp;
import com.moewadeul.basecamp.domain.BasecampRepository;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import java.time.Instant;
import java.time.LocalDate;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class BasecampAutoTransitionApplier {

    private final BasecampRepository basecampRepository;
    private final OutboxEventRecorder outboxEventRecorder;

    @Transactional
    boolean processDayBeforeDeparture(long basecampId, Instant now) {
        return transition(basecampId, basecamp -> basecamp.departDayBefore(now));
    }

    @Transactional
    boolean complete(long basecampId, LocalDate today, Instant now) {
        return transition(basecampId, basecamp -> basecamp.complete(today, now));
    }

    private boolean transition(long basecampId, Predicate<Basecamp> transition) {
        // 베이스캠프 행을 쓰기 잠금으로 읽는 조회가 이 트랜잭션의 첫 조회여야 한다. MySQL의 반복 가능한 읽기에서는
        // 잠그지 않는 조회가 먼저 일어나면 그 시점에 읽기 스냅숏이 정해져서, 잠금을 얻은 뒤에도 먼저 끝난 탈퇴 같은
        // 다른 트랜잭션의 커밋이 보이지 않는다. 그러면 이미 빠진 멤버를 세어 인원을 잘못 판단한다.
        Basecamp basecamp = basecampRepository.findByIdForUpdate(basecampId).orElse(null);
        if (basecamp == null || !transition.test(basecamp)) {
            return false;
        }
        outboxEventRecorder.recordAll(basecamp.pullEvents());
        return true;
    }
}
