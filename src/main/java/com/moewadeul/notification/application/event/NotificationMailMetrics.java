package com.moewadeul.notification.application.event;

import com.moewadeul.notification.infra.event.NotificationMailBacklogRow;
import com.moewadeul.notification.infra.event.NotificationMailMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NotificationMailMetrics implements MeterBinder {

    private static final String MAILS_GAUGE_NAME = "moewadeul.notification.mails";
    private static final String OLDEST_PENDING_AGE_GAUGE_NAME = "moewadeul.notification.mail.oldest.pending.age";

    private final NotificationMailMapper mapper;
    private final Clock clock;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(MAILS_GAUGE_NAME, this, metrics -> metrics.backlog().pendingCount())
                .description("상태별 알림 메일 수")
                .tag("status", "pending")
                .register(registry);
        Gauge.builder(MAILS_GAUGE_NAME, this, metrics -> metrics.backlog().failedCount())
                .description("상태별 알림 메일 수")
                .tag("status", "failed")
                .register(registry);
        Gauge.builder(OLDEST_PENDING_AGE_GAUGE_NAME, this, NotificationMailMetrics::oldestPendingAgeSeconds)
                .description("가장 오래된 보낼 메일의 알림이 생긴 뒤 지난 시간. 보낼 메일이 없으면 0")
                .baseUnit("seconds")
                .register(registry);
    }

    double oldestPendingAgeSeconds() {
        Instant oldest = backlog().oldestPendingCreatedAt();
        if (oldest == null) {
            return 0;
        }
        return Math.max(0, Duration.between(oldest, clock.instant()).toMillis() / 1000.0);
    }

    private NotificationMailBacklogRow backlog() {
        return mapper.selectBacklog();
    }
}
