package com.moewadeul.program.application.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.moewadeul.common.outbox.OutboxLane;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.outbox.application.OutboxPublisher;
import com.moewadeul.program.application.ProgramApplyFixture;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ProgramSeatReleaseBurstIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final int CAPACITY = 20;
    private static final int SUBSCRIBERS = 200;
    private static final int EMAIL_LIMIT = 50;
    private static final int THREADS = 6;

    @Autowired
    private ProgramPaymentExpiryService expiryService;

    @Autowired
    private ProgramPaymentService programPaymentService;

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private ProgramApplyFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
    }

    @Test
    @DisplayName("[F-20][PG-07] 매진 행사의 결제 대기 신청이 한 번의 만료 실행에 모두 만료돼도 빈자리 이벤트는 1건이고 메일은 50통을 넘지 않는다")
    void burstExpiryNotifiesOnce() {
        // given
        long programId = fixture.saveProgram(CAPACITY, false);
        List<Long> applicants = fixture.saveEmailVerifiedMembers(CAPACITY);
        for (long applicant : applicants) {
            fixture.saveApplication(programId, applicant, "PENDING_PAYMENT", NOW.minus(Duration.ofMinutes(1)));
        }
        List<Long> subscribers = fixture.saveEmailVerifiedMembers(SUBSCRIBERS);
        fixture.saveVacancyAlerts(programId, subscribers);

        // when
        ProgramPaymentExpiryService.Result result = expiryService.run();
        publishAll();

        // then
        assertThat(result.expired()).isEqualTo(CAPACITY);
        assertThat(fixture.seatReleasedPayloads(programId)).hasSize(1);
        assertThat(seatReleasedNotificationCount()).isEqualTo(SUBSCRIBERS);
        assertThat(seatReleasedPendingEmailCount()).isLessThanOrEqualTo(EMAIL_LIMIT);
    }

    @RepeatedTest(5)
    @DisplayName("[F-20][PG-07] 같은 행사에서 본인 취소 여러 건이 같은 순간에 자리를 돌려줘도 알림 신청자는 빈자리 이벤트 하나에만 들어간다")
    void concurrentReleasesNotifyEachSubscriberOnce() throws Exception {
        // given
        long programId = fixture.saveProgram(THREADS, false);
        List<Long> cancelers = fixture.saveEmailVerifiedMembers(THREADS);
        List<Long> applicationIds = new ArrayList<>();
        for (long canceler : cancelers) {
            applicationIds.add(fixture.saveApplication(programId, canceler, "PENDING_PAYMENT"));
        }
        List<Long> subscribers = fixture.saveEmailVerifiedMembers(10);
        fixture.saveVacancyAlerts(programId, subscribers);

        // when
        cancelConcurrently(cancelers, applicationIds);

        // then
        List<Long> notified = fixture.seatReleasedPayloads(programId).stream()
                .flatMap(payload -> JsonPath.<List<Number>>read(payload, "$.memberIds").stream())
                .map(Number::longValue)
                .toList();
        assertThat(notified).containsExactlyInAnyOrderElementsOf(subscribers);
    }

    private void cancelConcurrently(List<Long> cancelers, List<Long> applicationIds) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            long canceler = cancelers.get(i);
            long applicationId = applicationIds.get(i);
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return programPaymentService.cancel(canceler, applicationId);
            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    private void publishAll() {
        while (publisher.publishPending(OutboxLane.DEFAULT) > 0) {}
    }

    private int seatReleasedNotificationCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE type = 'PROGRAM_SEAT_RELEASED'", Integer.class);
    }

    private int seatReleasedPendingEmailCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE type = 'PROGRAM_SEAT_RELEASED' AND email_status = 'PENDING'",
                Integer.class);
    }
}
