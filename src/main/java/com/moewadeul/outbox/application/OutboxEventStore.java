package com.moewadeul.outbox.application;

import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.common.retry.RetryPolicy;
import com.moewadeul.outbox.infra.OutboxEventMapper;
import com.moewadeul.outbox.infra.OutboxMessageRow;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class OutboxEventStore {

    private final OutboxEventMapper mapper;
    private final OutboxProperties properties;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    OutboxEventStore(OutboxEventMapper mapper, OutboxProperties properties, Clock clock) {
        this.mapper = mapper;
        this.properties = properties;
        this.retryPolicy = properties.retryPolicy();
        this.clock = clock;
    }

    @Transactional
    public Claim claim(Instant now) {
        List<Long> ids = mapper.selectClaimableIds(now, properties.batchSize());
        Instant leaseUntil = retryPolicy.leaseUntil(now);
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
        int updated = mapper.markFailedAttempt(eventId, error, transientFailure, retryPolicy, clock.instant());
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
