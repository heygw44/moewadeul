package com.moewadeul.outbox.application;

import com.moewadeul.common.metrics.BacklogSnapshot;
import com.moewadeul.common.metrics.PolledMetric;
import com.moewadeul.common.metrics.PolledSnapshot;
import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxLane;
import com.moewadeul.outbox.infra.OutboxBacklogRow;
import com.moewadeul.outbox.infra.OutboxEventMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics implements MeterBinder, PolledMetric {

    private static final String EVENTS_GAUGE_NAME = "moewadeul.outbox.events";
    private static final String OLDEST_PENDING_AGE_GAUGE_NAME = "moewadeul.outbox.oldest.pending.age";
    private static final BacklogSnapshot EMPTY = new BacklogSnapshot(0, 0, 0);

    private final OutboxEventMapper mapper;
    private final Clock clock;
    private final Set<String> mailEventTypes;
    private final PolledSnapshot<Map<OutboxLane, BacklogSnapshot>> backlog;

    public OutboxMetrics(OutboxEventMapper mapper, Clock clock, List<OutboxEventHandler> handlers) {
        this.mapper = mapper;
        this.clock = clock;
        this.mailEventTypes = OutboxLane.mailEventTypes(handlers);
        this.backlog = new PolledSnapshot<>(this::readBacklog, clock);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (OutboxLane lane : OutboxLane.values()) {
            String laneTag = lane.name().toLowerCase(Locale.ROOT);
            Gauge.builder(EVENTS_GAUGE_NAME, backlog, read(lane, BacklogSnapshot::pending))
                    .description("줄과 상태별 아웃박스 이벤트 수")
                    .tag("lane", laneTag)
                    .tag("status", "pending")
                    .register(registry);
            Gauge.builder(EVENTS_GAUGE_NAME, backlog, read(lane, BacklogSnapshot::failed))
                    .description("줄과 상태별 아웃박스 이벤트 수")
                    .tag("lane", laneTag)
                    .tag("status", "failed")
                    .register(registry);
            Gauge.builder(OLDEST_PENDING_AGE_GAUGE_NAME, backlog, read(lane, BacklogSnapshot::oldestPendingAgeSeconds))
                    .description("줄마다 가장 오래된 미발행 이벤트가 기록된 뒤 지난 시간. 미발행 이벤트가 없으면 0")
                    .tag("lane", laneTag)
                    .baseUnit("seconds")
                    .register(registry);
        }
    }

    @Override
    public String job() {
        return "outbox-metrics-refresh";
    }

    @Override
    public void refresh() {
        backlog.refresh();
    }

    private static ToDoubleFunction<PolledSnapshot<Map<OutboxLane, BacklogSnapshot>>> read(
            OutboxLane lane, ToDoubleFunction<BacklogSnapshot> field) {
        return snapshot -> snapshot.read(byLane -> field.applyAsDouble(byLane.getOrDefault(lane, EMPTY)));
    }

    private Map<OutboxLane, BacklogSnapshot> readBacklog() {
        Instant now = clock.instant();
        Map<OutboxLane, BacklogSnapshot> byLane = new EnumMap<>(OutboxLane.class);
        for (OutboxBacklogRow row : mapper.selectBacklog(mailEventTypes)) {
            byLane.put(
                    row.lane(),
                    BacklogSnapshot.at(now, row.pendingCount(), row.failedCount(), row.oldestPendingCreatedAt()));
        }
        return byLane;
    }
}
