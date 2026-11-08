package com.moewadeul.common.outbox;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public enum OutboxLane {
    MAIL,
    DEFAULT;

    public static Set<String> mailEventTypes(Collection<? extends OutboxEventHandler> handlers) {
        return handlers.stream()
                .filter(handler -> handler.lane() == MAIL)
                .flatMap(handler -> handler.eventTypes().stream())
                .collect(Collectors.toUnmodifiableSet());
    }
}
