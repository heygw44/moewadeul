package com.moewadeul.notification.application.event;

import com.moewadeul.common.retry.RetryPolicy;
import com.moewadeul.notification.application.NotificationEventTypes;
import com.moewadeul.notification.infra.event.NotificationMailMapper;
import com.moewadeul.notification.infra.event.NotificationMailRow;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
class NotificationMailStore {

    private final NotificationMailMapper mailMapper;
    private final NotificationMailProperties properties;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    NotificationMailStore(NotificationMailMapper mailMapper, NotificationMailProperties properties, Clock clock) {
        this.mailMapper = mailMapper;
        this.properties = properties;
        this.retryPolicy = properties.retryPolicy();
        this.clock = clock;
    }

    @Transactional
    Claim claim(Instant now) {
        Instant leaseUntil = retryPolicy.leaseUntil(now);
        int remaining = remainingQuota(now);
        if (remaining == 0) {
            return new Claim(leaseUntil, List.of());
        }
        List<NotificationMailRow> rows = mailMapper.selectDueForUpdate(
                now, Math.min(properties.batchSize(), remaining), NotificationEventTypes.DEFAULT_EMAIL_ENABLED);
        List<Long> skipped = rows.stream()
                .filter(row -> !row.deliverable())
                .map(NotificationMailRow::notificationId)
                .toList();
        List<NotificationMailRow> targets =
                rows.stream().filter(NotificationMailRow::deliverable).toList();
        if (!skipped.isEmpty()) {
            mailMapper.markSkipped(skipped);
        }
        if (!targets.isEmpty()) {
            mailMapper.lease(
                    targets.stream().map(NotificationMailRow::notificationId).toList(), leaseUntil);
        }
        return new Claim(leaseUntil, targets);
    }

    private int remainingQuota(Instant now) {
        int sentInWindow = mailMapper.countSentAfter(now.minus(NotificationEventTypes.EMAIL_LIMIT_WINDOW));
        return Math.max(0, NotificationEventTypes.EMAIL_LIMIT_PER_WINDOW - sentInWindow);
    }

    @Transactional
    int releaseLease(Instant leaseUntil, List<Long> notificationIds) {
        return mailMapper.releaseLease(notificationIds, leaseUntil);
    }

    @Transactional
    void markSent(long notificationId) {
        if (mailMapper.markSent(notificationId, clock.instant()) == 0) {
            log.warn("notification mail was not pending when marking sent notificationId={}", notificationId);
        }
    }

    @Transactional
    void markFailedAttempt(long notificationId, boolean transientFailure) {
        int updated = mailMapper.markFailedAttempt(notificationId, transientFailure, retryPolicy, clock.instant());
        if (updated == 0) {
            log.warn("notification mail was not pending when marking failed notificationId={}", notificationId);
        }
    }

    record Claim(Instant leaseUntil, List<NotificationMailRow> targets) {}
}
