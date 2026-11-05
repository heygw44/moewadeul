package com.moewadeul.outbox.application;

import com.moewadeul.common.metrics.BacklogSnapshot;
import com.moewadeul.common.metrics.PolledMetric;
import com.moewadeul.common.metrics.PolledSnapshot;
import com.moewadeul.outbox.infra.OutboxBacklogRow;
import com.moewadeul.outbox.infra.OutboxEventMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics implements MeterBinder, PolledMetric {

    private static final String EVENTS_GAUGE_NAME = "moewadeul.outbox.events";
    private static final String OLDEST_PENDING_AGE_GAUGE_NAME = "moewadeul.outbox.oldest.pending.age";

    private final OutboxEventMapper mapper;
    private final Clock clock;
    private final PolledSnapshot<BacklogSnapshot> backlog;

    public OutboxMetrics(OutboxEventMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
        this.backlog = new PolledSnapshot<>(this::readBacklog, clock);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(EVENTS_GAUGE_NAME, backlog, snapshot -> snapshot.read(BacklogSnapshot::pending))
                .description("상태별 아웃박스 이벤트 수")
                .tag("status", "pending")
                .register(registry);
        Gauge.builder(EVENTS_GAUGE_NAME, backlog, snapshot -> snapshot.read(BacklogSnapshot::failed))
                .description("상태별 아웃박스 이벤트 수")
                .tag("status", "failed")
                .register(registry);
        Gauge.builder(
                        OLDEST_PENDING_AGE_GAUGE_NAME,
                        backlog,
                        snapshot -> snapshot.read(BacklogSnapshot::oldestPendingAgeSeconds))
                .description("가장 오래된 미발행 이벤트가 기록된 뒤 지난 시간. 미발행 이벤트가 없으면 0")
                .baseUnit("seconds")
                .register(registry);
    }

    @Override
    public String job() {
        return "outbox-metrics-refresh";
    }

    @Override
    public void refresh() {
        backlog.refresh();
    }

    private BacklogSnapshot readBacklog() {
        OutboxBacklogRow row = mapper.selectBacklog();
        return BacklogSnapshot.at(clock.instant(), row.pendingCount(), row.failedCount(), row.oldestPendingCreatedAt());
    }
}
