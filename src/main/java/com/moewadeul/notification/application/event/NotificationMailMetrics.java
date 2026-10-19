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

/**
 * 아직 보내지 못한 알림 메일을 운영 지표로 내보낸다.
 *
 * <p>메일 발송이 이벤트 처리와 따로 돌기 때문에, 메일 서버 장애나 발송 작업 멈춤은 아웃박스 지표에 드러나지 않는다. 그래서 운영자는 가장 오래된
 * 보낼 메일의 나이로 발송이 멈췄는지 알아챈다. FAILED 수는 재시도 한도를 넘겨 보내지 못한 메일 수다.
 *
 * <p>값은 지표를 수집할 때마다 DB에서 읽는다. 수집 주기가 1분이고 조회가 인덱스 구간만 읽어서 부담이 작다.
 */
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
