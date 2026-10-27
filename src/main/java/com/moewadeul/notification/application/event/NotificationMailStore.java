package com.moewadeul.notification.application.event;

import com.moewadeul.notification.application.NotificationEventTypes;
import com.moewadeul.notification.infra.event.NotificationMailMapper;
import com.moewadeul.notification.infra.event.NotificationMailRow;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
        // 컬럼이 마이크로초까지만 저장하므로, 나노초가 붙은 시각은 저장한 값과 같은지 비교할 때 어긋날 수 있다.
        Instant leaseUntil = now.plus(properties.lease()).truncatedTo(ChronoUnit.MICROS);
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
        int updated = mailMapper.markFailedAttempt(
                notificationId,
                transientFailure,
                properties.maxAttempts(),
                properties.retryDelay().toMillis(),
                properties.retryMaxDelay().toMillis(),
                clock.instant());
        if (updated == 0) {
            log.warn("notification mail was not pending when marking failed notificationId={}", notificationId);
        }
    }

    record Claim(Instant leaseUntil, List<NotificationMailRow> targets) {}
}
