package com.moewadeul.common.outbox;

import java.time.Instant;

public record OutboxMessage(
        long eventId, String eventType, String aggregateType, long aggregateId, String payload, Instant occurredAt) {}
