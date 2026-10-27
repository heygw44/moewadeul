package com.moewadeul.notification.application.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "moewadeul.notification.mail",
        name = "sender-enabled",
        havingValue = "true",
        matchIfMissing = true)
class NotificationMailScheduler {

    private final NotificationMailer mailer;

    @Scheduled(
            fixedDelayString =
                    "${moewadeul.notification.mail.poll-delay:" + NotificationMailProperties.DEFAULT_POLL_DELAY + "}")
    void send() {
        int sent = mailer.sendDue();
        if (sent > 0) {
            log.debug("notification mails processed count={}", sent);
        }
    }
}
