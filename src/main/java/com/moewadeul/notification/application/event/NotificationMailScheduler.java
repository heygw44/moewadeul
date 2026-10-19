package com.moewadeul.notification.application.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// fixedDelay는 이전 발송이 끝난 뒤부터 간격을 센다. 그래서 같은 인스턴스에서 발송이 겹치지 않는다.
// 인스턴스가 여러 대여도 NotificationMailStore가 SKIP LOCKED와 임대로 알림을 나눠 집으므로 겹쳐 실행해도 된다.
// 아웃박스 발행기와 다른 스케줄러 스레드에서 돌도록 스케줄러 스레드를 여러 개 둔다(application.yml의 spring.task.scheduling.pool.size).
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
