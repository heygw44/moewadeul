package com.moewadeul.notification.application.event;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.notification.mail",
        name = "sender-enabled",
        havingValue = "true",
        matchIfMissing = true)
class NotificationMailScheduler {

    private final NotificationMailer mailer;

    NotificationMailScheduler(
            NotificationMailer mailer,
            NotificationMailProperties properties,
            @Value("${moewadeul.mail.send-timeout}") Duration sendTimeout,
            @Value("${spring.task.scheduling.pool.size:1}") int schedulingPoolSize) {
        requireLeaseOutlastsBatch(properties, sendTimeout);
        requireSchedulerThreadNotShared(schedulingPoolSize);
        this.mailer = mailer;
    }

    @Scheduled(
            fixedDelayString =
                    "${moewadeul.notification.mail.poll-delay:" + NotificationMailProperties.DEFAULT_POLL_DELAY + "}")
    void send() {
        int sent = mailer.sendDue();
        if (sent > 0) {
            log.debug("notification mails processed count={}", sent);
        }
    }

    private static void requireLeaseOutlastsBatch(NotificationMailProperties properties, Duration sendTimeout) {
        Duration worstBatch = sendTimeout.multipliedBy(properties.batchSize());
        if (properties.lease().compareTo(worstBatch) <= 0) {
            throw new IllegalStateException("moewadeul.notification.mail.lease(" + properties.lease()
                    + ") must be longer than batchSize x moewadeul.mail.send-timeout(" + worstBatch + ")");
        }
    }

    private static void requireSchedulerThreadNotShared(int schedulingPoolSize) {
        if (schedulingPoolSize < 2) {
            throw new IllegalStateException("spring.task.scheduling.pool.size must be at least 2 so that"
                    + " the notification mail sender does not block the outbox publisher");
        }
    }
}
