package com.moewadeul.common.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class OutboxEventRecorder {

    private final OutboxEventJpaRepository outboxEventRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public void record(String eventType, String aggregateType, long aggregateId, Object payload) {
        requireActiveTransaction();
        String payloadJson = toJson(payload);
        outboxEventRepository.save(
                OutboxEvent.pending(eventType, aggregateType, aggregateId, payloadJson, Instant.now(clock)));
    }

    public void recordAll(List<OutboxEventDraft> events) {
        events.forEach(event -> record(event.eventType(), event.aggregateType(), event.aggregateId(), event.payload()));
    }

    public boolean hasPending(String eventType, String aggregateType, long aggregateId) {
        return outboxEventRepository.existsByEventTypeAndAggregateTypeAndAggregateIdAndStatus(
                eventType, aggregateType, aggregateId, OutboxEventStatus.PENDING);
    }

    private void requireActiveTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("outbox 이벤트는 원래 작업과 같은 트랜잭션 안에서 기록해야 합니다.");
        }
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
