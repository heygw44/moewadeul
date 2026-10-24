package com.moewadeul.spot.application.moderation;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.spot.application.bakji.BakjiFeedbackService;
import com.moewadeul.spot.application.bakji.BakjiProblemReportCommand;
import com.moewadeul.spot.application.publicspot.PublicSpotCommand;
import com.moewadeul.spot.application.publicspot.PublicSpotSource;
import com.moewadeul.spot.application.publicspot.PublicSpotSyncService;
import com.moewadeul.spot.domain.GeoPoint;
import com.moewadeul.spot.domain.ParkAreaJudgement;
import com.moewadeul.spot.domain.Spot;
import com.moewadeul.spot.domain.SpotErrorCode;
import com.moewadeul.spot.infra.SpotJpaRepository;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotModerationConcurrencyIntegrationTest {

    private static final int PENDING_REVIEW_REPORTS = 5;
    private static final String EXTERNAL_ID = "100";
    private static final BakjiProblemReportCommand REPORT = new BakjiProblemReportCommand("CLOSED", "길이 막혔다");

    @Autowired
    private SpotModerationService spotModerationService;

    @Autowired
    private BakjiFeedbackService bakjiFeedbackService;

    @Autowired
    private SpotJpaRepository spotRepository;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PublicSpotSyncService publicSpotSyncService;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName("[F-21] 같은 장소를 관리자 2명이 동시에 숨기면 1건만 성공하고 나머지는 SPOT_INVALID_STATE이며 감사 로그는 한 줄이다")
    void concurrentHidesSucceedOnce() throws Exception {
        // given
        long spotId = saveBakji();
        long firstAdmin = saveMember();
        long secondAdmin = saveMember();

        // when
        List<Throwable> failures = runConcurrently(List.of(
                () -> spotModerationService.hide(spotId, firstAdmin),
                () -> spotModerationService.hide(spotId, secondAdmin)));

        // then
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SpotErrorCode.SPOT_INVALID_STATE));
        assertThat(statusOf(spotId)).isEqualTo("HIDDEN");
        assertThat(auditCount("SPOT_HIDE")).isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[F-21] 같은 장소를 동시에 복구하면 1건만 성공하고 나머지는 SPOT_INVALID_STATE이며 감사 로그는 한 줄이다")
    void concurrentRestoresSucceedOnce() throws Exception {
        // given
        long spotId = saveBakji();
        long admin = saveMember();
        spotModerationService.hide(spotId, admin);
        jdbc.update("DELETE FROM admin_audit_log");

        // when
        List<Throwable> failures = runConcurrently(List.of(
                () -> spotModerationService.restore(spotId, admin),
                () -> spotModerationService.restore(spotId, admin)));

        // then
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SpotErrorCode.SPOT_INVALID_STATE));
        assertThat(statusOf(spotId)).isEqualTo("ACTIVE");
        assertThat(auditCount("SPOT_RESTORE")).isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[F-08][F-21] 복구와 신고가 동시에 오면 신고가 복구 전이든 뒤든 검토 전 신고 수와 박지 상태가 일관된다")
    void restoreRacingReportStaysConsistent() throws Exception {
        // given
        long spotId = saveBakji();
        for (int i = 0; i < PENDING_REVIEW_REPORTS - 1; i++) {
            bakjiFeedbackService.reportProblem(saveMember(), spotId, REPORT);
        }
        long fifthReporter = saveMember();
        bakjiFeedbackService.reportProblem(fifthReporter, spotId, REPORT);
        assertThat(statusOf(spotId)).isEqualTo("PENDING_REVIEW");
        long admin = saveMember();
        // 검토 대기 박지는 신고를 받지 않으므로, 신고가 복구 뒤에 들어오는 경우만 성공한다.
        long lateReporter = saveMember();

        // when
        List<Throwable> failures = runConcurrently(List.of(() -> spotModerationService.restore(spotId, admin), () -> {
            bakjiFeedbackService.reportProblem(lateReporter, spotId, REPORT);
            return null;
        }));

        // then
        long totalReports = count("SELECT COUNT(*) FROM bakji_report WHERE spot_id = ?", spotId);
        long unreviewed = count("SELECT COUNT(*) FROM bakji_report WHERE spot_id = ? AND reviewed_at IS NULL", spotId);
        assertThat(statusOf(spotId)).isEqualTo("ACTIVE");
        if (failures.isEmpty()) {
            // 복구가 먼저 끝나서 늦은 신고를 새로 센다. 이전 신고 5건은 검토 완료로 표시됐다.
            assertThat(totalReports).isEqualTo(PENDING_REVIEW_REPORTS + 1);
            assertThat(unreviewed).isEqualTo(1);
        } else {
            // 신고가 먼저 와서 검토 대기 박지를 찾지 못해 NOT_FOUND로 거부됐다.
            assertThat(failures).hasSize(1);
            assertThat(failures.get(0))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e -> assertThat(e.getErrorCode().name()).isEqualTo("NOT_FOUND"));
            assertThat(totalReports).isEqualTo(PENDING_REVIEW_REPORTS);
            assertThat(unreviewed).isZero();
        }
        assertThat(auditCount("SPOT_RESTORE")).isEqualTo(1);
    }

    @RepeatedTest(20)
    @DisplayName("[F-06][F-21] 관리자 숨김과 동기화 원천 삭제가 같은 공공데이터 장소에 동시에 와도 데드락 없이 어느 한쪽이 숨기고, 원천 삭제 표시는 동기화가 숨긴 때만 남는다")
    void hideRacingSourceRemovalStaysConsistent() throws Exception {
        // given
        long spotId = savePublicSpot();
        long admin = saveMember();
        AtomicInteger hiddenBySync = new AtomicInteger();

        // when
        List<Throwable> failures = runConcurrently(List.of(() -> spotModerationService.hide(spotId, admin), () -> {
            hiddenBySync.set(publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of(EXTERNAL_ID)));
            return null;
        }));

        // then
        assertThat(statusOf(spotId)).isEqualTo("HIDDEN");
        if (failures.isEmpty()) {
            // 관리자가 먼저 숨겼다. 동기화는 관리자가 숨긴 장소를 원천 삭제로 표시하지 않는다.
            assertThat(hiddenBySync.get()).isZero();
            assertThat(sourceRemovedAt(spotId)).isNull();
            assertThat(auditCount("SPOT_HIDE")).isEqualTo(1);
        } else {
            // 동기화가 먼저 숨겼다. 관리자 숨김은 잠금을 기다린 뒤 이미 숨긴 상태를 보고 거부됐다.
            assertThat(failures).hasSize(1);
            assertThat(failures.get(0))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(SpotErrorCode.SPOT_INVALID_STATE));
            assertThat(hiddenBySync.get()).isEqualTo(1);
            assertThat(sourceRemovedAt(spotId)).isNotNull();
            assertThat(auditCount("SPOT_HIDE")).isZero();
        }
    }

    @RepeatedTest(20)
    @DisplayName("[F-06][F-21] 관리자가 숨긴 공공데이터 장소의 복구와 동기화 원천 삭제가 동시에 와도 데드락 없이 복구는 성공하고, 동기화는 그 장소를 숨기지 않는다")
    void restoreRacingSourceRemovalStaysConsistent() throws Exception {
        // given
        long spotId = savePublicSpot();
        long admin = saveMember();
        spotModerationService.hide(spotId, admin);
        AtomicInteger hiddenBySync = new AtomicInteger();

        // when
        List<Throwable> failures = runConcurrently(List.of(() -> spotModerationService.restore(spotId, admin), () -> {
            hiddenBySync.set(publicSpotSyncService.markRemoved(PublicSpotSource.GOCAMPING, List.of(EXTERNAL_ID)));
            return null;
        }));

        // then: 숨김 상태일 때는 동기화의 후보가 아니고, 복구가 커밋된 뒤에는 장소가 ACTIVE이면서 표시를 가져 후보에서 빠진다.
        assertThat(failures).isEmpty();
        assertThat(hiddenBySync.get()).isZero();
        assertThat(statusOf(spotId)).isEqualTo("ACTIVE");
        assertThat(sourceRemovedAt(spotId)).isNotNull();
    }

    private long savePublicSpot() {
        publicSpotSyncService.upsert(
                PublicSpotSource.GOCAMPING,
                List.of(new PublicSpotCommand(
                        EXTERNAL_ID,
                        "솔숲 야영장",
                        37.8813,
                        127.7298,
                        "강원 춘천시 남산면 1",
                        "일반야영장",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Set.of())));
        return jdbc.queryForObject(
                "SELECT spot_id FROM public_spot_detail WHERE source = 'GOCAMPING' AND external_id = ?",
                Long.class,
                EXTERNAL_ID);
    }

    private Timestamp sourceRemovedAt(long spotId) {
        return jdbc.queryForObject(
                "SELECT source_removed_at FROM public_spot_detail WHERE spot_id = ?", Timestamp.class, spotId);
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

    private String statusOf(long spotId) {
        return jdbc.queryForObject("SELECT status FROM spot WHERE id = ?", String.class, spotId);
    }

    private int auditCount(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE action = ?", Integer.class, action);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    // 작업마다 스레드 하나로 동시에 출발시키고, 던져진 예외를 모아 돌려준다.
    private List<Throwable> runConcurrently(List<Callable<Object>> tasks) throws Exception {
        int threads = tasks.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return collectFailures(futures);
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private List<Throwable> collectFailures(List<Future<Object>> futures) throws InterruptedException {
        List<Throwable> failures = new ArrayList<>();
        for (Future<Object> future : futures) {
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
}
