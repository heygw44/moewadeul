package com.moewadeul.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.outbox.OutboxEventHandler;
import com.moewadeul.common.outbox.OutboxEventRecorder;
import com.moewadeul.common.outbox.OutboxLane;
import com.moewadeul.common.outbox.OutboxMessage;
import com.moewadeul.common.scheduling.ShutdownSignal;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class OutboxLaneIntegrationTest {

    private static final String AGGREGATE_TYPE = "TEST_AGGREGATE";
    private static final String MAIL_EVENT_TYPE = "TEST_MAIL_EVENT";
    private static final String DEFAULT_EVENT_TYPE = "TEST_DEFAULT_EVENT";
    private static final String UNKNOWN_EVENT_TYPE = "UNKNOWN_EVENT";
    private static final long TIMEOUT_SECONDS = 30;

    @Autowired
    private OutboxEventStore store;

    @Autowired
    private OutboxEventRecorder recorder;

    @Autowired
    private MutableClock clock;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final CountDownLatch mailHandlerEntered = new CountDownLatch(1);
    private final CountDownLatch releaseMailHandler = new CountDownLatch(1);

    @Test
    @DisplayName("[NFR-04] 메일 줄 처리기가 멈춰 있어도 기본 줄 이벤트는 바로 발행된다")
    void blockedMailLaneDoesNotStopDefaultLane() throws Exception {
        // given
        long mailEventId = record(MAIL_EVENT_TYPE);
        long defaultEventId = record(DEFAULT_EVENT_TYPE);
        OutboxPublisher publisher = publisher(blockingMailHandler(), defaultHandler());
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<Integer> mailLane = executor.submit(() -> publisher.publishPending(OutboxLane.MAIL));
            assertThat(mailHandlerEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isTrue();

            // when
            int defaultProcessed = publisher.publishPending(OutboxLane.DEFAULT);

            // then
            assertThat(defaultProcessed).isEqualTo(1);
            assertThat(status(defaultEventId)).isEqualTo("PUBLISHED");
            assertThat(status(mailEventId)).isEqualTo("PENDING");

            releaseMailHandler.countDown();
            assertThat(mailLane.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(status(mailEventId)).isEqualTo("PUBLISHED");
        } finally {
            releaseMailHandler.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("[NFR-04] 각 줄은 자기 줄 이벤트만 집는다")
    void eachLaneClaimsOnlyItsOwnEvents() {
        // given
        long mailEventId = record(MAIL_EVENT_TYPE);
        long defaultEventId = record(DEFAULT_EVENT_TYPE);
        releaseMailHandler.countDown();
        OutboxPublisher publisher = publisher(blockingMailHandler(), defaultHandler());

        // when
        int mailProcessed = publisher.publishPending(OutboxLane.MAIL);

        // then
        assertThat(mailProcessed).isEqualTo(1);
        assertThat(status(mailEventId)).isEqualTo("PUBLISHED");
        assertThat(status(defaultEventId)).isEqualTo("PENDING");

        // when
        int defaultProcessed = publisher.publishPending(OutboxLane.DEFAULT);

        // then
        assertThat(defaultProcessed).isEqualTo(1);
        assertThat(status(defaultEventId)).isEqualTo("PUBLISHED");
        assertThat(publisher.publishPending(OutboxLane.MAIL)).isZero();
    }

    @Test
    @DisplayName("[NFR-04] 처리기가 없는 이벤트 종류는 기본 줄이 집어 처리기 없음 오류로 기록하고 메일 줄은 건드리지 않는다")
    void eventWithoutHandlerStaysInDefaultLane() {
        // given
        long eventId = record(UNKNOWN_EVENT_TYPE);
        OutboxPublisher publisher = publisher(blockingMailHandler(), defaultHandler());

        // when
        int mailProcessed = publisher.publishPending(OutboxLane.MAIL);

        // then
        assertThat(mailProcessed).isZero();
        assertThat(attemptCount(eventId)).isZero();

        // when
        int defaultProcessed = publisher.publishPending(OutboxLane.DEFAULT);

        // then
        assertThat(defaultProcessed).isEqualTo(1);
        assertThat(attemptCount(eventId)).isEqualTo(1);
        assertThat(lastError(eventId)).isEqualTo("no handler for " + UNKNOWN_EVENT_TYPE);
    }

    @Test
    @DisplayName("[NFR-04] 메일 처리기가 하나도 없으면 메일 줄은 아무것도 집지 않고 기본 줄이 모든 이벤트를 집는다")
    void withoutMailHandlersDefaultLaneClaimsEverything() {
        // given
        long eventId = record(DEFAULT_EVENT_TYPE);
        OutboxPublisher publisher = publisher(defaultHandler());

        // when
        int mailProcessed = publisher.publishPending(OutboxLane.MAIL);
        int defaultProcessed = publisher.publishPending(OutboxLane.DEFAULT);

        // then
        assertThat(mailProcessed).isZero();
        assertThat(defaultProcessed).isEqualTo(1);
        assertThat(status(eventId)).isEqualTo("PUBLISHED");
    }

    private OutboxPublisher publisher(OutboxEventHandler... handlers) {
        return new OutboxPublisher(store, clock, List.of(handlers), new ShutdownSignal());
    }

    private OutboxEventHandler blockingMailHandler() {
        return new OutboxEventHandler() {
            @Override
            public Set<String> eventTypes() {
                return Set.of(MAIL_EVENT_TYPE);
            }

            @Override
            public OutboxLane lane() {
                return OutboxLane.MAIL;
            }

            @Override
            public void handle(OutboxMessage message) {
                mailHandlerEntered.countDown();
                try {
                    releaseMailHandler.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
    }

    private static OutboxEventHandler defaultHandler() {
        return new OutboxEventHandler() {
            @Override
            public Set<String> eventTypes() {
                return Set.of(DEFAULT_EVENT_TYPE);
            }

            @Override
            public void handle(OutboxMessage message) {}
        };
    }

    private long record(String eventType) {
        transactionTemplate.executeWithoutResult(
                status -> recorder.record(eventType, AGGREGATE_TYPE, 1L, Map.of("sequence", 1)));
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM outbox_event WHERE event_type = ?", Long.class, eventType);
    }

    private String status(long eventId) {
        return jdbcTemplate.queryForObject("SELECT status FROM outbox_event WHERE id = ?", String.class, eventId);
    }

    private int attemptCount(long eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM outbox_event WHERE id = ?", Integer.class, eventId);
    }

    private String lastError(long eventId) {
        return jdbcTemplate.queryForObject("SELECT last_error FROM outbox_event WHERE id = ?", String.class, eventId);
    }
}
