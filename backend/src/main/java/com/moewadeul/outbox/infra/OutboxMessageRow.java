package com.moewadeul.outbox.infra;

import java.time.Instant;

public record OutboxMessageRow(
        long id, String eventType, String aggregateType, long aggregateId, String payload, Instant createdAt) {}
