package com.moewadeul.outbox.application;

import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.outbox.infra.OutboxEventMapper;
import com.moewadeul.outbox.infra.OutboxMessageRow;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventStore {

    private final OutboxEventMapper mapper;
    private final OutboxProperties properties;
    private final Clock clock;

    @Transactional
    public Claim claim(Instant now) {
        List<Long> ids = mapper.selectClaimableIds(now, properties.batchSize());
        // 컬럼이 마이크로초까지만 저장하므로, 나노초가 붙은 시각은 저장한 값과 같은지 비교할 때 어긋날 수 있다.
        Instant leaseUntil = now.plus(properties.lease()).truncatedTo(ChronoUnit.MICROS);
        if (ids.isEmpty()) {
            return new Claim(leaseUntil, List.of());
        }
        mapper.lease(ids, leaseUntil, now);
        return new Claim(
                leaseUntil,
                mapper.selectByIds(ids).stream()
                        .map(OutboxEventStore::toMessage)
                        .toList());
    }

    @Transactional
    public int releaseLease(Instant leaseUntil, List<Long> eventIds) {
        return mapper.releaseLease(eventIds, leaseUntil, clock.instant());
    }

    @Transactional
    public void markPublished(long eventId) {
        int updated = mapper.markPublished(eventId, clock.instant());
        if (updated == 0) {
            log.warn("outbox event was not pending when marking published eventId={}", eventId);
        }
    }

    @Transactional
    public void markFailedAttempt(long eventId, String error, boolean transientFailure) {
        int updated = mapper.markFailedAttempt(
                eventId,
                error,
                transientFailure,
                properties.maxAttempts(),
                properties.retryDelay().toMillis(),
                properties.retryMaxDelay().toMillis(),
                clock.instant());
        if (updated == 0) {
            log.warn("outbox event was not pending when marking failed eventId={}", eventId);
        }
    }

    private static OutboxMessage toMessage(OutboxMessageRow row) {
        return new OutboxMessage(
                row.id(), row.eventType(), row.aggregateType(), row.aggregateId(), row.payload(), row.createdAt());
    }

    public record Claim(Instant leaseUntil, List<OutboxMessage> messages) {}
}
