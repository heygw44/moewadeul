package com.moewadeul.notification.application.event;

import com.moewadeul.common.mail.MailProperties;
import com.moewadeul.common.scheduling.ScheduledJobRunner;
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

    private static final String JOB_NAME = "notification-mail";

    private final NotificationMailer mailer;
    private final ScheduledJobRunner jobRunner;

    NotificationMailScheduler(
            NotificationMailer mailer,
            ScheduledJobRunner jobRunner,
            NotificationMailProperties properties,
            MailProperties mailProperties,
            @Value("${spring.task.scheduling.pool.size:1}") int schedulingPoolSize) {
        properties.requireLeaseOutlastsBatch(mailProperties.sendTimeout());
        requireSchedulerThreadNotShared(schedulingPoolSize);
        this.mailer = mailer;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
    }

    @Scheduled(
            fixedDelayString =
                    "${moewadeul.notification.mail.poll-delay:" + NotificationMailProperties.DEFAULT_POLL_DELAY + "}")
    void send() {
        jobRunner.run(JOB_NAME, () -> {
            int sent = mailer.sendDue();
            if (sent > 0) {
                log.debug("notification mails processed count={}", sent);
            }
        });
    }

    private static void requireSchedulerThreadNotShared(int schedulingPoolSize) {
        if (schedulingPoolSize < 2) {
            throw new IllegalStateException("spring.task.scheduling.pool.size must be at least 2 so that"
                    + " the notification mail sender does not block the outbox publisher");
        }
    }
}
