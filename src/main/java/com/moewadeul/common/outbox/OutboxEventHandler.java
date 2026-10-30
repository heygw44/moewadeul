package com.moewadeul.common.outbox;

import java.util.Set;

public interface OutboxEventHandler {

    Set<String> eventTypes();

    default OutboxLane lane() {
        return OutboxLane.DEFAULT;
    }

    void handle(OutboxMessage message);
}
