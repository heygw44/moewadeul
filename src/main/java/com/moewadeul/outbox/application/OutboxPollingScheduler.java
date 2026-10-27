package com.moewadeul.outbox.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "moewadeul.outbox",
        name = "publisher-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxPollingScheduler {

    private final OutboxPublisher publisher;

    @Scheduled(fixedDelayString = "${moewadeul.outbox.poll-delay:" + OutboxProperties.DEFAULT_POLL_DELAY + "}")
    public void poll() {
        int processed = publisher.publishPending();
        if (processed > 0) {
            log.debug("outbox events processed count={}", processed);
        }
    }
}
