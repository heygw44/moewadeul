package com.moewadeul.common.outbox;

import com.moewadeul.common.transaction.InsideTransaction;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class OutboxEventRecorder {

    private final OutboxEventJpaRepository outboxEventRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public void record(String eventType, String aggregateType, long aggregateId, Object payload) {
        recordDeferred(eventType, aggregateType, aggregateId, payload, Duration.ZERO);
    }

    public void recordDeferred(
            String eventType, String aggregateType, long aggregateId, Object payload, Duration delay) {
        InsideTransaction.require("outbox 이벤트는 원래 작업과 같은 트랜잭션에서 기록해야 한다.");
        Instant now = Instant.now(clock);
        Instant lockedUntil = delay.isZero() ? null : now.plus(delay);
        outboxEventRepository.save(
                OutboxEvent.pendingUntil(eventType, aggregateType, aggregateId, toJson(payload), now, lockedUntil));
    }

    public void recordAll(List<OutboxEventDraft> events) {
        events.forEach(event -> record(event.eventType(), event.aggregateType(), event.aggregateId(), event.payload()));
    }

    public boolean hasPending(String eventType, String aggregateType, long aggregateId) {
        return outboxEventRepository.existsByEventTypeAndAggregateTypeAndAggregateIdAndStatus(
                eventType, aggregateType, aggregateId, OutboxEventStatus.PENDING);
    }

    private String toJson(Object payload) {
        if (payload == null) {
            throw new IllegalArgumentException("이벤트 payload가 null입니다.");
        }
        try {
            return jsonMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(
                    "이벤트 payload를 JSON으로 바꿀 수 없습니다. payload 타입="
                            + payload.getClass().getName(),
                    e);
        }
    }
}
