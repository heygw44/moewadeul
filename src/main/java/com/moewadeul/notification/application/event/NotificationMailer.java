package com.moewadeul.notification.application.event;

import com.moewadeul.common.mail.MailDeliveryException;
import com.moewadeul.common.mail.MailSender;
import com.moewadeul.common.scheduling.ShutdownSignal;
import com.moewadeul.notification.infra.event.NotificationMailRow;
import java.time.Clock;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class NotificationMailer {

    private final NotificationMailStore store;
    private final MailSender mailSender;
    private final Clock clock;
    private final String baseUrl;
    private final ShutdownSignal shutdownSignal;

    NotificationMailer(
            NotificationMailStore store,
            MailSender mailSender,
            Clock clock,
            @Value("${moewadeul.web.base-url}") String baseUrl,
            ShutdownSignal shutdownSignal) {
        this.store = store;
        this.mailSender = mailSender;
        this.clock = clock;
        this.baseUrl = baseUrl;
        this.shutdownSignal = shutdownSignal;
    }

    int sendDue() {
        if (shutdownSignal.isRequested()) {
            return 0;
        }
        NotificationMailStore.Claim claim = store.claim(clock.instant());
        List<NotificationMailRow> targets = claim.targets();
        int handled = 0;
        while (handled < targets.size() && !shutdownSignal.isRequested()) {
            sendOne(targets.get(handled));
            handled++;
        }
        if (handled < targets.size()) {
            releaseUnsent(claim, handled);
        }
        return handled;
    }

    private void releaseUnsent(NotificationMailStore.Claim claim, int handled) {
        List<Long> unsentIds = claim.targets().subList(handled, claim.targets().size()).stream()
                .map(NotificationMailRow::notificationId)
                .toList();
        int released = store.releaseLease(claim.leaseUntil(), unsentIds);
        log.info(
                "notification mailer stopped for shutdown handled={} unsent={} released={}",
                handled,
                unsentIds.size(),
                released);
    }

    private void sendOne(NotificationMailRow target) {
        try {
            mailSender.send(NotificationMailFactory.create(target, baseUrl));
        } catch (RuntimeException e) {
            boolean transientFailure = MailDeliveryException.isTransient(e);
            log.warn(
                    "notification mail failed notificationId={} transient={}",
                    target.notificationId(),
                    transientFailure);
            store.markFailedAttempt(target.notificationId(), transientFailure);
            return;
        }
        store.markSent(target.notificationId());
    }
}
