package com.moewadeul.outbox.application;

import com.moewadeul.outbox.infra.OutboxBacklogRow;
import com.moewadeul.outbox.infra.OutboxEventMapper;
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
public class OutboxMetrics implements MeterBinder {

    private static final String EVENTS_GAUGE_NAME = "moewadeul.outbox.events";
    private static final String OLDEST_PENDING_AGE_GAUGE_NAME = "moewadeul.outbox.oldest.pending.age";

    private final OutboxEventMapper mapper;
    private final Clock clock;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(EVENTS_GAUGE_NAME, this, metrics -> metrics.backlog().pendingCount())
                .description("상태별 아웃박스 이벤트 수")
                .tag("status", "pending")
                .register(registry);
        Gauge.builder(EVENTS_GAUGE_NAME, this, metrics -> metrics.backlog().failedCount())
                .description("상태별 아웃박스 이벤트 수")
                .tag("status", "failed")
                .register(registry);
        Gauge.builder(OLDEST_PENDING_AGE_GAUGE_NAME, this, OutboxMetrics::oldestPendingAgeSeconds)
                .description("가장 오래된 미발행 이벤트가 기록된 뒤 지난 시간. 미발행 이벤트가 없으면 0")
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

    private OutboxBacklogRow backlog() {
        return mapper.selectBacklog();
    }
}
