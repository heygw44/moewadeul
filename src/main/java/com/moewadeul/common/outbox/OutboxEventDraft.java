package com.moewadeul.common.outbox;

public record OutboxEventDraft(String eventType, String aggregateType, long aggregateId, Object payload) {}
