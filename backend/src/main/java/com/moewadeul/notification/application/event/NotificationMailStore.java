package com.moewadeul.notification.application.event;

import com.moewadeul.notification.application.NotificationEventTypes;
import com.moewadeul.notification.infra.event.NotificationMailMapper;
import com.moewadeul.notification.infra.event.NotificationMailRow;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class NotificationMailStore {

    private final NotificationMailMapper mailMapper;
    private final NotificationMailProperties properties;
    private final Clock clock;

    @Transactional
    Claim claim(Instant now) {
        Instant leaseUntil = properties.retryPolicy().leaseUntil(now);
        int sentInWindow = mailMapper.countSentAfter(now.minus(NotificationEventTypes.EMAIL_LIMIT_WINDOW));
        int remaining = Math.max(0, NotificationEventTypes.EMAIL_LIMIT_PER_WINDOW - sentInWindow);
        List<NotificationMailRow> rows =
                new ArrayList<>(select(now, Math.min(properties.batchSize(), remaining), false));
        int bulkRemaining = NotificationEventTypes.BULK_EMAIL_LIMIT_PER_WINDOW - sentInWindow - rows.size();
        int bulkLimit =
                Math.min(Math.min(properties.batchSize() - rows.size(), remaining - rows.size()), bulkRemaining);
        if (bulkLimit > 0) {
            rows.addAll(select(now, bulkLimit, true));
        }
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

    private List<NotificationMailRow> select(Instant now, int limit, boolean bulk) {
        if (limit <= 0) {
            return List.of();
        }
        return mailMapper.selectDueForUpdate(now, limit, bulk, NotificationEventTypes.DEFAULT_EMAIL_ENABLED);
    }

    @Transactional
    int releaseLease(Instant leaseUntil, List<Long> notificationIds) {
        return mailMapper.releaseLease(notificationIds, leaseUntil);
    }

    @Transactional
    int releaseLeasesOfPreviousRun() {
        return mailMapper.releaseFirstAttemptLeases(clock.instant());
    }

    @Transactional
    void markSent(long notificationId, Instant leaseUntil) {
        if (mailMapper.markSent(notificationId, leaseUntil, clock.instant()) == 0) {
            log.warn(
                    "notification mail was not pending or its lease was lost when marking sent notificationId={}",
                    notificationId);
        }
    }

    @Transactional
    void markFailedAttempt(long notificationId, Instant leaseUntil, boolean transientFailure) {
        int updated = mailMapper.markFailedAttempt(
                notificationId, leaseUntil, transientFailure, properties.retryPolicy(), clock.instant());
        if (updated == 0) {
            log.warn(
                    "notification mail was not pending or its lease was lost when marking failed notificationId={}",
                    notificationId);
        }
    }

    record Claim(Instant leaseUntil, List<NotificationMailRow> targets) {}
}
