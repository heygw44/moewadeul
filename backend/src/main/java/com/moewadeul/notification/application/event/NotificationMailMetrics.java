package com.moewadeul.notification.application.event;

import com.moewadeul.common.metrics.BacklogSnapshot;
import com.moewadeul.common.metrics.PolledMetric;
import com.moewadeul.common.metrics.PolledSnapshot;
import com.moewadeul.notification.infra.event.NotificationMailBacklogRow;
import com.moewadeul.notification.infra.event.NotificationMailMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
class NotificationMailMetrics implements MeterBinder, PolledMetric {

    private static final String MAILS_GAUGE_NAME = "moewadeul.notification.mails";
    private static final String OLDEST_PENDING_AGE_GAUGE_NAME = "moewadeul.notification.mail.oldest.pending.age";

    private final NotificationMailMapper mapper;
    private final Clock clock;
    private final PolledSnapshot<BacklogSnapshot> backlog;

    NotificationMailMetrics(NotificationMailMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
        this.backlog = new PolledSnapshot<>(this::readBacklog, clock);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(MAILS_GAUGE_NAME, backlog, snapshot -> snapshot.read(BacklogSnapshot::pending))
                .description("상태별 알림 메일 수")
                .tag("status", "pending")
                .register(registry);
        Gauge.builder(MAILS_GAUGE_NAME, backlog, snapshot -> snapshot.read(BacklogSnapshot::failed))
                .description("상태별 알림 메일 수")
                .tag("status", "failed")
                .register(registry);
        Gauge.builder(
                        OLDEST_PENDING_AGE_GAUGE_NAME,
                        backlog,
                        snapshot -> snapshot.read(BacklogSnapshot::oldestPendingAgeSeconds))
                .description("가장 오래된 보낼 메일의 알림이 생긴 뒤 지난 시간. 보낼 메일이 없으면 0")
                .baseUnit("seconds")
                .register(registry);
    }

    @Override
    public String job() {
        return "notification-mail-metrics-refresh";
    }

    @Override
    public void refresh() {
        backlog.refresh();
    }

    private BacklogSnapshot readBacklog() {
        NotificationMailBacklogRow row = mapper.selectBacklog();
        return BacklogSnapshot.at(clock.instant(), row.pendingCount(), row.failedCount(), row.oldestPendingCreatedAt());
    }
}
