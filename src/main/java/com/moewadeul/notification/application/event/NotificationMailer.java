package com.moewadeul.notification.application.event;

import com.moewadeul.common.mail.MailSender;
import com.moewadeul.notification.infra.event.NotificationMailRow;
import java.time.Clock;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 보낼 차례인 알림 메일을 집어 보내고, 결과를 알림마다 기록한다.
 *
 * <p>이 클래스에는 {@code @Transactional}을 걸지 않는다. 메일 서버 응답을 기다리는 동안 DB 커넥션을 잡지 않으려고, 집기와 결과 기록은
 * {@link NotificationMailStore}의 짧은 트랜잭션으로 따로 하고 메일은 그 밖에서 보낸다.
 *
 * <p>알림을 만드는 이벤트 처리와는 따로 돈다. 그래서 받는 회원이 많은 이벤트의 메일을 보내는 동안에도 아웃박스 발행기는 다른 이벤트를 계속
 * 처리한다. 한 통을 보내지 못하면 그 알림만 실패 횟수를 올리고 나중에 다시 보낸다. 메일을 보낸 직후부터 결과를 기록하기 전에 서버가 멈추면,
 * 임대가 끝난 뒤 같은 메일이 한 번 더 나갈 수 있다. 전달은 적어도 한 번이다.
 */
@Slf4j
@Component
class NotificationMailer {

    private final NotificationMailStore store;
    private final MailSender mailSender;
    private final Clock clock;
    private final String baseUrl;

    NotificationMailer(
            NotificationMailStore store,
            MailSender mailSender,
            Clock clock,
            @Value("${moewadeul.web.base-url}") String baseUrl) {
        this.store = store;
        this.mailSender = mailSender;
        this.clock = clock;
        this.baseUrl = baseUrl;
    }

    /**
     * 호출하면 보낼 차례인 알림 메일을 한 번 집어 모두 보낸다. 한 통이 실패해도 나머지는 계속 보낸다.
     *
     * @return 이번에 집어서 보내려 한 메일 수(실패 포함)
     */
    int sendDue() {
        List<NotificationMailRow> targets = store.claim(clock.instant());
        for (NotificationMailRow target : targets) {
            sendOne(target);
        }
        return targets.size();
    }

    // 발송기 구현마다 던지는 예외가 다르고, 한 통의 실패가 나머지 발송을 막으면 안 된다. 그래서 여기서만 RuntimeException을 통째로 잡는다.
    // 예외 메시지에는 받는 사람의 주소가 들어 있을 수 있으므로 로그에는 알림 ID만 남긴다.
    private void sendOne(NotificationMailRow target) {
        try {
            mailSender.send(NotificationMailFactory.create(target, baseUrl));
        } catch (RuntimeException e) {
            log.warn("notification mail failed notificationId={}", target.notificationId());
            store.markFailedAttempt(target.notificationId());
            return;
        }
        store.markSent(target.notificationId());
    }
}
