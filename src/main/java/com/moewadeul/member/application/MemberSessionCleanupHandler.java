package com.moewadeul.member.application;

import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.common.transaction.OutsideTransaction;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MemberSessionCleanupHandler implements OutboxEventHandler {

    private final MemberSessions memberSessions;

    @Override
    public Set<String> eventTypes() {
        return Set.of(MemberSessionCleanup.EVENT_TYPE);
    }

    @Override
    public void handle(OutboxMessage message) {
        OutsideTransaction.require("세션 삭제는 트랜잭션 안에서 부르면 커넥션을 하나 더 잡는다.");
        memberSessions.invalidateLoggedInBefore(message.aggregateId(), message.occurredAt());
    }
}
