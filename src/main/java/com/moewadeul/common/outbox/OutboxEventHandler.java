package com.moewadeul.common.outbox;

import java.util.Set;

public interface OutboxEventHandler {

    Set<String> eventTypes();

    void handle(OutboxMessage message);
}
