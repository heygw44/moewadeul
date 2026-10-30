package com.moewadeul.outbox.application;

import com.moewadeul.common.mail.MailProperties;
import com.moewadeul.common.outbox.OutboxLane;
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
    private static final String MAIL_JOB_NAME = "outbox-mail-polling";

    private final OutboxPublisher publisher;
    private final ScheduledJobRunner jobRunner;

    public OutboxPollingScheduler(
            OutboxPublisher publisher,
            ScheduledJobRunner jobRunner,
            OutboxProperties properties,
            MailProperties mailProperties) {
        properties.requireLeaseOutlastsBatch(mailProperties.sendTimeout());
        this.publisher = publisher;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
        jobRunner.register(MAIL_JOB_NAME);
    }

    @Scheduled(fixedDelayString = "${moewadeul.outbox.poll-delay:" + OutboxProperties.DEFAULT_POLL_DELAY + "}")
    public void poll() {
        jobRunner.run(JOB_NAME, () -> publish(OutboxLane.DEFAULT));
    }

    @Scheduled(fixedDelayString = "${moewadeul.outbox.poll-delay:" + OutboxProperties.DEFAULT_POLL_DELAY + "}")
    public void pollMail() {
        jobRunner.run(MAIL_JOB_NAME, () -> publish(OutboxLane.MAIL));
    }

    private void publish(OutboxLane lane) {
        int processed = publisher.publishPending(lane);
        if (processed > 0) {
            log.debug("outbox events processed lane={} count={}", lane, processed);
        }
    }
}
