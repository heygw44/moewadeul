package com.moewadeul.outbox.application;

import com.moewadeul.common.mail.MailProperties;
import com.moewadeul.common.outbox.OutboxLane;
import com.moewadeul.common.scheduling.ScheduledJobRunner;
import lombok.extern.slf4j.Slf4j;
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
        prefix = "moewadeul.outbox",
        name = "publisher-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxPollingScheduler implements ApplicationListener<ContextRefreshedEvent> {

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

    // 서버는 한 대이고 배포는 옛 컨테이너를 내린 뒤 새 컨테이너를 띄운다. 그래서 기동할 때 남아 있는 임대는 죽은 프로세스의 것이다.
    // 임대가 끝나기를 10분 기다리지 않고 푼다. @Order로 스케줄 시작(같은 이벤트, 가장 낮은 순위)보다 먼저 돌아 이번 실행이 집은 이벤트를 풀 일이 없다.
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        publisher.releaseLeasesOfPreviousRun();
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
