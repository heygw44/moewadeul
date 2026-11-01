package com.moewadeul.common.mail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class TestMailSender implements MailSender {

    public static final String FAILURE_MESSAGE = "simulated mail delivery failure";

    private final List<MailMessage> sent = new CopyOnWriteArrayList<>();
    private final AtomicInteger remainingFailures = new AtomicInteger();
    private volatile boolean transientFailures;

    @Override
    public void send(MailMessage message) {
        if (shouldFail()) {
            throw new MailDeliveryException(FAILURE_MESSAGE, null, transientFailures);
        }
        sent.add(message);
    }

    public List<MailMessage> sent() {
        return List.copyOf(sent);
    }

    public void failNextSends(int count) {
        transientFailures = false;
        remainingFailures.set(count);
    }

    public void failNextSendsTransiently(int count) {
        transientFailures = true;
        remainingFailures.set(count);
    }

    public void reset() {
        sent.clear();
        transientFailures = false;
        remainingFailures.set(0);
    }

    private boolean shouldFail() {
        return remainingFailures.getAndUpdate(remaining -> remaining > 0 ? remaining - 1 : 0) > 0;
    }
}
