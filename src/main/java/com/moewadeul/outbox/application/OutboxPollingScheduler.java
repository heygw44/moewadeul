package com.moewadeul.outbox.application;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.outbox",
        name = "publisher-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxPollingScheduler {

    private static final String JOB_NAME = "outbox-polling";

    private final OutboxPublisher publisher;
    private final ScheduledJobRunner jobRunner;

    public OutboxPollingScheduler(OutboxPublisher publisher, ScheduledJobRunner jobRunner) {
        this.publisher = publisher;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
    }

    @Scheduled(fixedDelayString = "${moewadeul.outbox.poll-delay:" + OutboxProperties.DEFAULT_POLL_DELAY + "}")
    public void poll() {
        jobRunner.run(JOB_NAME, () -> {
            int processed = publisher.publishPending();
            if (processed > 0) {
                log.debug("outbox events processed count={}", processed);
            }
        });
    }
}
