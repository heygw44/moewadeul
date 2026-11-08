package com.moewadeul.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class OutboxMetricsIntegrationTest {

    private static final String AGGREGATE_TYPE = "TEST_AGGREGATE";
    private static final String MAIL_EVENT_TYPE = "EMAIL_VERIFICATION_REQUESTED";

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private OutboxEventRecorder recorder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @Autowired
    private OutboxMetrics outboxMetrics;

    @Test
    @DisplayName("[NFR-09] 미발행 이벤트가 없으면 미발행 수, 실패 수, 가장 오래된 미발행 이벤트의 나이가 모두 0이다")
    void reportsZeroWhenNothingIsPending() {
        outboxMetrics.refresh();

        assertThat(events("pending")).isZero();
        assertThat(events("failed")).isZero();
        assertThat(oldestPendingAgeSeconds()).isZero();
    }

    @Test
    @DisplayName("[NFR-09] 상태별 이벤트 수와 가장 오래된 미발행 이벤트가 기록된 뒤 지난 시간을 지표로 읽는다")
    void reportsBacklogFromDatabase() {
        // given
        transactionTemplate.executeWithoutResult(status -> {
            for (int sequence = 1; sequence <= 3; sequence++) {
                recorder.record(
                        RecordingOutboxEventHandler.EVENT_TYPE, AGGREGATE_TYPE, sequence, Map.of("sequence", sequence));
            }
        });
        jdbcTemplate.update(
                "UPDATE outbox_event SET status = 'FAILED' WHERE id = (SELECT id FROM (SELECT MIN(id) AS id FROM outbox_event) t)");

        // when
        clock.advance(Duration.ofMinutes(15));
        outboxMetrics.refresh();

        // then
        assertThat(events("pending")).isEqualTo(2);
        assertThat(events("failed")).isEqualTo(1);
        assertThat(oldestPendingAgeSeconds()).isEqualTo(900);
    }

    @Test
    @DisplayName("[NFR-09] 발행을 마친 이벤트는 미발행 수와 나이에 들어가지 않는다")
    void ignoresPublishedEvents() {
        transactionTemplate.executeWithoutResult(status ->
                recorder.record(RecordingOutboxEventHandler.EVENT_TYPE, AGGREGATE_TYPE, 1L, Map.of("sequence", 1)));
        jdbcTemplate.update("UPDATE outbox_event SET status = 'PUBLISHED'");
        clock.advance(Duration.ofHours(1));
        outboxMetrics.refresh();

        assertThat(events("pending")).isZero();
        assertThat(oldestPendingAgeSeconds()).isZero();
    }

    @Test
    @DisplayName("[NFR-09] 메일 처리기가 맡은 이벤트는 mail 줄로, 나머지는 default 줄로 나눠 세어 메일 적체와 기본 줄 적체를 구별한다")
    void splitsBacklogByLane() {
        // given
        transactionTemplate.executeWithoutResult(status -> {
            recorder.record(MAIL_EVENT_TYPE, AGGREGATE_TYPE, 1L, Map.of("sequence", 1));
        });
        clock.advance(Duration.ofMinutes(10));
        transactionTemplate.executeWithoutResult(status -> {
            recorder.record(RecordingOutboxEventHandler.EVENT_TYPE, AGGREGATE_TYPE, 2L, Map.of("sequence", 2));
            recorder.record(RecordingOutboxEventHandler.EVENT_TYPE, AGGREGATE_TYPE, 3L, Map.of("sequence", 3));
        });

        // when
        clock.advance(Duration.ofMinutes(1));
        outboxMetrics.refresh();

        // then
        assertThat(events("mail", "pending")).isEqualTo(1);
        assertThat(oldestPendingAgeSeconds("mail")).isEqualTo(660);
        assertThat(events("default", "pending")).isEqualTo(2);
        assertThat(oldestPendingAgeSeconds("default")).isEqualTo(60);
    }

    private double events(String status) {
        return events("default", status);
    }

    private double events(String lane, String status) {
        return meterRegistry
                .get("moewadeul.outbox.events")
                .tag("lane", lane)
                .tag("status", status)
                .gauge()
                .value();
    }

    private double oldestPendingAgeSeconds() {
        return oldestPendingAgeSeconds("default");
    }

    private double oldestPendingAgeSeconds(String lane) {
        return meterRegistry
                .get("moewadeul.outbox.oldest.pending.age")
                .tag("lane", lane)
                .gauge()
                .value();
    }
}
