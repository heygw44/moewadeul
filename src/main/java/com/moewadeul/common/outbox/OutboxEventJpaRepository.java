package com.moewadeul.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long> {

    boolean existsByEventTypeAndAggregateTypeAndAggregateIdAndStatus(
            String eventType, String aggregateType, long aggregateId, OutboxEventStatus status);
}
