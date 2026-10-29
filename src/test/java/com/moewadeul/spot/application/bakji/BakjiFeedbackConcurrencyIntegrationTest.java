package com.moewadeul.spot.application.bakji;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.spot.domain.GeoPoint;
import com.moewadeul.spot.domain.ParkAreaJudgement;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.domain.SpotErrorCode;
import com.moewadeul.spot.infra.SpotJpaRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class BakjiFeedbackConcurrencyIntegrationTest {

    private static final long MISSING_MEMBER_ID = 999_999_999L;

    private static final int THREADS = 10;
    private static final int PENDING_REVIEW_REPORTS = 5;
    private static final BakjiProblemReportCommand REPORT = new BakjiProblemReportCommand("CLOSED", "길이 막혔다");

    @Autowired
    private BakjiFeedbackService bakjiFeedbackService;

    @Autowired
    private SpotJpaRepository spotRepository;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @RepeatedTest(5)
    @DisplayName("[F-08] 신고 3건인 박지에 서로 다른 회원 2명이 동시에 신고하면 둘 다 저장하고 박지가 PENDING_REVIEW가 된다")
    void concurrentReportsCrossingThresholdMoveToPendingReview() throws Exception {
        // given
        long spotId = saveBakji();
        for (int i = 0; i < PENDING_REVIEW_REPORTS - 2; i++) {
            bakjiFeedbackService.reportProblem(saveMember(), spotId, REPORT);
        }
        List<Long> memberIds = saveMembers(2);

        // when
        List<Throwable> failures = runConcurrently(memberIds, memberId -> {
            bakjiFeedbackService.reportProblem(memberId, spotId, REPORT);
            return null;
        });

        // then
        assertThat(failures).isEmpty();
        assertThat(reportCount(spotId)).isEqualTo(PENDING_REVIEW_REPORTS);
        assertThat(statusOf(spotId)).isEqualTo("PENDING_REVIEW");
    }

    @RepeatedTest(5)
    @DisplayName("[F-08] 신고가 없는 박지에 서로 다른 회원 10명이 동시에 신고하면 5건째에 PENDING_REVIEW가 되고, 그 뒤 신고는 NOT_FOUND이며 신고 행은 5건이다")
    void concurrentReportsStopAtThreshold() throws Exception {
        // given
        long spotId = saveBakji();
        List<Long> memberIds = saveMembers(THREADS);

        // when
        List<Throwable> failures = runConcurrently(memberIds, memberId -> {
            bakjiFeedbackService.reportProblem(memberId, spotId, REPORT);
            return null;
        });

        // then
        assertThat(failures)
                .hasSize(THREADS - PENDING_REVIEW_REPORTS)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode().name()).isEqualTo("NOT_FOUND")));
        assertThat(reportCount(spotId)).isEqualTo(PENDING_REVIEW_REPORTS);
        assertThat(statusOf(spotId)).isEqualTo("PENDING_REVIEW");
    }

    @RepeatedTest(5)
    @DisplayName("[F-08] 같은 회원이 같은 박지를 동시에 신고하면 1건만 성공하고 나머지는 BAKJI_ALREADY_REPORTED다")
    void sameMemberReportsOnlyOnce() throws Exception {
        // given
        long spotId = saveBakji();
        long memberId = saveMember();

        // when
        List<Throwable> failures = runConcurrently(sameMember(memberId), id -> {
            bakjiFeedbackService.reportProblem(id, spotId, REPORT);
            return null;
        });

        // then
        assertThat(failures)
                .hasSize(THREADS - 1)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(SpotErrorCode.BAKJI_ALREADY_REPORTED)));
        assertThat(reportCount(spotId)).isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[F-08] 같은 회원이 같은 박지를 동시에 확인하면 1건만 성공하고 나머지는 BAKJI_ALREADY_CONFIRMED다")
    void sameMemberConfirmsOnlyOnce() throws Exception {
        // given
        long spotId = saveBakji();
        long memberId = saveMember();

        // when
        List<Throwable> failures =
                runConcurrently(sameMember(memberId), id -> bakjiFeedbackService.confirm(id, spotId));

        // then
        assertThat(failures)
                .hasSize(THREADS - 1)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(SpotErrorCode.BAKJI_ALREADY_CONFIRMED)));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM bakji_confirmation WHERE spot_id = ?", Integer.class, spotId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[F-08] 다른 트랜잭션이 박지 행을 잠근 채 놓지 않으면 신고는 MySQL 기본값 50초가 아니라 약 5초 기다린 뒤 잠금 대기 실패로 끝난다")
    void reportGivesUpWaitingForLockedBakjiAfterAboutFiveSeconds() throws Exception {
        // given
        long spotId = saveBakji();
        long memberId = saveMember();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService holder = Executors.newSingleThreadExecutor();
        try {
            Future<?> holding = holder.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT id FROM spot WHERE id = ? FOR UPDATE", Long.class, spotId);
                locked.countDown();
                awaitQuietly(release);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            // when
            long startedAt = System.nanoTime();
            Throwable thrown = catchThrowable(() -> bakjiFeedbackService.reportProblem(memberId, spotId, REPORT));
            Duration waited = Duration.ofNanos(System.nanoTime() - startedAt);

            // then: 잠금 대기 제한이 없으면 MySQL 기본값인 50초를 기다린다.
            assertThat(thrown).isInstanceOf(PessimisticLockingFailureException.class);
            assertThat(waited).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(15));
            release.countDown();
            holding.get(10, TimeUnit.SECONDS);
            assertThat(reportCount(spotId)).isZero();
        } finally {
            release.countDown();
            holder.shutdown();
            assertThat(holder.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            assertThat(latch.await(60, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static List<Long> sameMember(long memberId) {
        return Collections.nCopies(THREADS, memberId);
    }

    @Test
    @DisplayName("[F-08] 확인한 회원이 없어서 외래 키가 깨지면 이미 확인했다고 바꾸지 않고 무결성 위반을 그대로 던진다")
    void confirmForeignKeyViolationIsNotReportedAsDuplicate() {
        // given
        long spotId = saveBakji();

        // when, then
        assertThatThrownBy(() -> bakjiFeedbackService.confirm(MISSING_MEMBER_ID, spotId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("[F-08] 신고자가 없어서 외래 키가 깨지면 이미 신고했다고 바꾸지 않고 무결성 위반을 그대로 던진다")
    void reportForeignKeyViolationIsNotReportedAsDuplicate() {
        // given
        long spotId = saveBakji();

        // when, then
        assertThatThrownBy(() -> bakjiFeedbackService.reportProblem(MISSING_MEMBER_ID, spotId, REPORT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long saveBakji() {
        Spot spot = Spot.bakji(
                "능선 끝 평지",
                new GeoPoint(37.25, 127.25),
                ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT),
                MutableClock.DEFAULT_INSTANT);
        return spotRepository.save(spot).getId();
    }

    private long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    private List<Long> saveMembers(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(saveMember());
        }
        return ids;
    }

    private int reportCount(long spotId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM bakji_report WHERE spot_id = ?", Integer.class, spotId);
    }

    private String statusOf(long spotId) {
        return jdbc.queryForObject("SELECT status FROM spot WHERE id = ?", String.class, spotId);
    }

    private <T> List<Throwable> runConcurrently(List<Long> memberIds, MemberAction<T> action) throws Exception {
        int threads = memberIds.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (long memberId : memberIds) {
                Callable<T> task = () -> {
                    ready.countDown();
                    start.await();
                    return action.run(memberId);
                };
                futures.add(executor.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return collectFailures(futures);
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private <T> List<Throwable> collectFailures(List<Future<T>> futures) throws InterruptedException {
        List<Throwable> failures = new ArrayList<>();
        for (Future<T> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            } catch (TimeoutException e) {
                failures.add(e);
            }
        }
        return failures;
    }

    @FunctionalInterface
    private interface MemberAction<T> {
        T run(long memberId) throws Exception;
    }
}
