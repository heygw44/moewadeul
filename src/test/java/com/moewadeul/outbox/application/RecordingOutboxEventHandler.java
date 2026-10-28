package com.moewadeul.outbox.application;

import com.moewadeul.common.mail.MailDeliveryException;
import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxMessage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

public class RecordingOutboxEventHandler implements OutboxEventHandler {

    public static final String EVENT_TYPE = "TEST_EVENT";
    public static final String FAILURE_MESSAGE = "simulated handler failure";

    private final List<Long> calledEventIds = new CopyOnWriteArrayList<>();
    private final AtomicInteger remainingFailures = new AtomicInteger();
    private volatile boolean transientFailures;

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(OutboxMessage message) {
        calledEventIds.add(message.eventId());
        if (!shouldFail()) {
            return;
        }
        if (transientFailures) {
            throw new IllegalStateException(
                    FAILURE_MESSAGE, new MailDeliveryException("mail server unreachable", null, true));
        }
        throw new IllegalStateException(FAILURE_MESSAGE);
    }

    public void failNextCalls(int count) {
        transientFailures = false;
        remainingFailures.set(count);
    }

    public void failNextCallsTransiently(int count) {
        transientFailures = true;
        remainingFailures.set(count);
    }

    public List<Long> calledEventIds() {
        return List.copyOf(calledEventIds);
    }

    public int callCount() {
        return calledEventIds.size();
    }

    public void reset() {
        calledEventIds.clear();
        transientFailures = false;
        remainingFailures.set(0);
    }

    private boolean shouldFail() {
        return remainingFailures.getAndUpdate(remaining -> remaining > 0 ? remaining - 1 : 0) > 0;
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        RecordingOutboxEventHandler recordingOutboxEventHandler() {
            return new RecordingOutboxEventHandler();
        }
    }
}
