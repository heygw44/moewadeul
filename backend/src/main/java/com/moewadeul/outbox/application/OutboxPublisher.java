package com.moewadeul.outbox.application;

import com.moewadeul.common.mail.MailDeliveryException;
import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxLane;
import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.common.scheduling.ShutdownSignal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OutboxPublisher {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final OutboxEventStore store;
    private final Clock clock;
    private final Map<String, OutboxEventHandler> handlersByEventType;
    private final Set<String> mailEventTypes;
    private final ShutdownSignal shutdownSignal;

    public OutboxPublisher(
            OutboxEventStore store, Clock clock, List<OutboxEventHandler> handlers, ShutdownSignal shutdownSignal) {
        this.store = store;
        this.clock = clock;
        this.handlersByEventType = indexByEventType(handlers);
        this.mailEventTypes = handlersByEventType.entrySet().stream()
                .filter(entry -> entry.getValue().lane() == OutboxLane.MAIL)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
        this.shutdownSignal = shutdownSignal;
    }

    public int publishPending(OutboxLane lane) {
        if (shutdownSignal.isRequested()) {
            return 0;
        }
        OutboxEventStore.Claim claim = store.claim(clock.instant(), lane, mailEventTypes);
        List<OutboxMessage> messages = claim.messages();
        int processed = 0;
        while (processed < messages.size() && !shutdownSignal.isRequested()) {
            publish(claim.leaseUntil(), messages.get(processed));
            processed++;
        }
        if (processed < messages.size()) {
            releaseUnprocessed(claim, processed);
        }
        return processed;
    }

    private void releaseUnprocessed(OutboxEventStore.Claim claim, int processed) {
        List<Long> unprocessedIds = claim
                .messages()
                .subList(processed, claim.messages().size())
                .stream()
                .map(OutboxMessage::eventId)
                .toList();
        int released = store.releaseLease(claim.leaseUntil(), unprocessedIds);
        log.info(
                "outbox publisher stopped for shutdown processed={} unprocessed={} released={}",
                processed,
                unprocessedIds.size(),
                released);
    }

    private void publish(Instant leaseUntil, OutboxMessage message) {
        Optional<Failure> failure = handle(message);
        if (failure.isEmpty()) {
            store.markPublished(message.eventId(), leaseUntil);
            return;
        }
        log.warn(
                "outbox event failed eventId={} eventType={} transient={}",
                message.eventId(),
                message.eventType(),
                failure.get().transientFailure());
        store.markFailedAttempt(
                message.eventId(),
                leaseUntil,
                failure.get().error(),
                failure.get().transientFailure());
    }

    private Optional<Failure> handle(OutboxMessage message) {
        OutboxEventHandler handler = handlersByEventType.get(message.eventType());
        if (handler == null) {
            return Optional.of(new Failure(truncate("no handler for " + message.eventType()), false));
        }
        try {
            handler.handle(message);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(new Failure(describe(e), MailDeliveryException.isTransient(e)));
        }
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        String description = e.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return truncate(description);
    }

    private static String truncate(String text) {
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }

    private static Map<String, OutboxEventHandler> indexByEventType(List<OutboxEventHandler> handlers) {
        Map<String, OutboxEventHandler> index = new HashMap<>();
        for (OutboxEventHandler handler : handlers) {
            for (String eventType : handler.eventTypes()) {
                OutboxEventHandler previous = index.put(eventType, handler);
                if (previous != null) {
                    throw new IllegalStateException("duplicate outbox event handler for eventType=" + eventType + ": "
                            + previous.getClass().getName() + ", "
                            + handler.getClass().getName());
                }
            }
        }
        return Map.copyOf(index);
    }

    private record Failure(String error, boolean transientFailure) {}
}
