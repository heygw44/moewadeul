package com.moewadeul.common.outbox;

public interface OutboxEventHandler {

    String eventType();

    void handle(OutboxMessage message);
}
