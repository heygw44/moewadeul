package com.moewadeul.notification.application.event;

import com.moewadeul.common.mail.MailProperties;
import com.moewadeul.common.scheduling.ScheduledJobRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(
        prefix = "moewadeul.notification.mail",
        name = "sender-enabled",
        havingValue = "true",
        matchIfMissing = true)
class NotificationMailScheduler implements ApplicationListener<ContextRefreshedEvent> {

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

    // 서버는 한 대이고 배포는 옛 컨테이너를 내린 뒤 새 컨테이너를 띄운다. 그래서 기동할 때 남아 있는 임대는 죽은 프로세스의 것이다.
    // @Order로 스케줄 시작(같은 이벤트, 가장 낮은 순위)보다 먼저 돌아 이번 실행이 집은 메일을 풀 일이 없다.
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        mailer.releaseLeasesOfPreviousRun();
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
