package com.moewadeul.spot.application.bakji;

import static com.moewadeul.common.testsupport.ProtectedAreaFixture.aProtectedArea;
import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.patch.PatchField;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.testsupport.ProtectedAreaFixture;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.spot.application.moderation.SpotModerationService;
import com.moewadeul.spot.application.park.ParkAreaJudgeService;
import com.moewadeul.spot.domain.bakji.BakjiDetailRepository;
import com.moewadeul.spot.infra.park.ParkAreaJudgeMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class BakjiUpdateRejudgeConcurrencyIntegrationTest {

    private static final int REPETITIONS = 5;
    private static final String AREA_LONG_LAT_WKT = "MULTIPOLYGON(((128 37, 129 37, 129 38, 128 38, 128 37)))";
    private static final double LAT = 37.6;
    private static final double INSIDE_LNG = 128.7;
    private static final double OUTSIDE_LNG = 127.7;
    private static final long MIDDLE_WAIT_SECONDS = 2;

    @Autowired
    private ProtectedAreaFixture protectedAreaFixture;

    @Autowired
    private BakjiCommandService bakjiCommandService;

    @Autowired
    private BakjiEditService bakjiEditService;

    @Autowired
    private BakjiFeedbackService bakjiFeedbackService;

    @Autowired
    private ParkAreaJudgeService parkAreaJudgeService;

    @Autowired
    private SpotModerationService spotModerationService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private final CountDownLatch paused = new CountDownLatch(1);
    private final CountDownLatch resume = new CountDownLatch(1);
    private final AtomicBoolean pauseArmed = new AtomicBoolean();
    private ExecutorService executor;
    private long reporterId;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        reporterId = memberRepository.saveAndFlush(aMember().build()).getId();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        PauseHooks.clear();
        resume.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-06][F-07][BC-04] 이름만 고치는 박지 수정이 재판정과 겹쳐도, 재판정이 새로 붙인 공원 경계 경고가 남는다")
    void renameDuringRejudgeKeepsNewWarning() throws Exception {
        long spotId = report(INSIDE_LNG);
        pauseUpdateAfterReadingSpot();

        Future<?> update = executor.submit(() -> bakjiEditService.update(reporterId, spotId, rename("새 이름")));
        awaitPaused();
        long areaId = protectedAreaFixture.insert(aProtectedArea().name("오대산").longLatBoundary(AREA_LONG_LAT_WKT));
        assertThat(runInMiddle(this::rejudgeAll, update)).isNull();

        assertThat(warningOf(spotId)).isTrue();
        assertThat(protectedAreaIdOf(spotId)).isEqualTo(areaId);
        assertThat(nameOf(spotId)).isEqualTo("새 이름");
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-06][F-07][BC-04] 재판정이 좌표를 읽은 뒤 박지를 공원 경계 안으로 옮기면, 옮긴 좌표의 경고가 남는다")
    void moveIntoAreaDuringRejudgeKeepsWarning() throws Exception {
        long areaId = protectedAreaFixture.insert(aProtectedArea().name("오대산").longLatBoundary(AREA_LONG_LAT_WKT));
        long spotId = report(OUTSIDE_LNG);
        pauseRejudgeAfterReadingPoints();

        Future<?> rejudge = executor.submit(this::rejudgeAll);
        awaitPaused();
        assertThat(runInMiddle(() -> bakjiEditService.update(reporterId, spotId, moveTo(INSIDE_LNG)), rejudge))
                .isNull();

        assertThat(lngOf(spotId)).isEqualTo(INSIDE_LNG);
        assertThat(warningOf(spotId)).isTrue();
        assertThat(protectedAreaIdOf(spotId)).isEqualTo(areaId);
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-07][F-21] 이름만 고치는 박지 수정이 관리자 숨김과 겹치면, 박지는 숨김 상태로 남고 수정은 NOT_FOUND로 거부된다")
    void renameDuringHideKeepsHidden() throws Exception {
        long spotId = report(OUTSIDE_LNG);
        long adminId = memberRepository.saveAndFlush(aMember().build()).getId();
        pauseUpdateAfterReadingSpot();

        Future<?> update = executor.submit(() -> bakjiEditService.update(reporterId, spotId, rename("새 이름")));
        awaitPaused();
        Throwable updateFailure = runInMiddle(() -> spotModerationService.hide(spotId, adminId), update);

        assertThat(statusOf(spotId)).isEqualTo("HIDDEN");
        assertThat(nameOf(spotId)).isEqualTo("능선 끝 평지");
        assertThat(updateFailure)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-07][F-21] 설명만 고치는 박지 수정이 관리자 숨김과 겹치면, 박지는 숨김 상태로 남고 수정은 NOT_FOUND로 거부된다")
    void describeDuringHideKeepsHiddenAndRejectsUpdate() throws Exception {
        long spotId = report(OUTSIDE_LNG);
        long adminId = memberRepository.saveAndFlush(aMember().build()).getId();
        pauseUpdateAfterReadingSpot();

        Future<?> update = executor.submit(() -> bakjiEditService.update(reporterId, spotId, describe("새 설명")));
        awaitPaused();
        Throwable updateFailure = runInMiddle(() -> spotModerationService.hide(spotId, adminId), update);

        assertThat(statusOf(spotId)).isEqualTo("HIDDEN");
        assertThat(descriptionOf(spotId)).isNull();
        assertThat(updateFailure)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-07][F-08] 설명만 고치는 박지 수정이 다섯 번째 신고와 겹치면, 박지는 검토 대기로 남고 수정은 NOT_FOUND로 거부된다")
    void describeDuringFifthReportKeepsPendingReviewAndRejectsUpdate() throws Exception {
        long spotId = report(OUTSIDE_LNG);
        for (int i = 0; i < 4; i++) {
            reportProblemBy(memberRepository.saveAndFlush(aMember().build()).getId(), spotId);
        }
        long fifthReporterId = memberRepository.saveAndFlush(aMember().build()).getId();
        pauseUpdateAfterReadingSpot();

        Future<?> update = executor.submit(() -> bakjiEditService.update(reporterId, spotId, describe("새 설명")));
        awaitPaused();
        Throwable updateFailure = runInMiddle(
                () -> {
                    reportProblemBy(fifthReporterId, spotId);
                    return null;
                },
                update);

        assertThat(statusOf(spotId)).isEqualTo("PENDING_REVIEW");
        assertThat(descriptionOf(spotId)).isNull();
        assertThat(updateFailure)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    private Void rejudgeAll() {
        Instant now = clock.instant();
        long afterId = 0;
        while (true) {
            Long lastId =
                    parkAreaJudgeService.rejudgeBakjiChunk(afterId, 1_000, now).lastId();
            if (lastId == null) {
                return null;
            }
            afterId = lastId;
        }
    }

    private long report(double lng) {
        return bakjiCommandService
                .report(reporterId, new BakjiReportCommand("능선 끝 평지", LAT, lng, null, true, false, null, null))
                .spotId();
    }

    private void reportProblemBy(long memberId, long spotId) {
        bakjiFeedbackService.reportProblem(memberId, spotId, new BakjiProblemReportCommand("FALSE_INFO", null));
    }

    private void pauseUpdateAfterReadingSpot() {
        pauseArmed.set(true);
        PauseHooks.beforeBakjiDetailRead = this::pauseOnce;
    }

    private void pauseRejudgeAfterReadingPoints() {
        pauseArmed.set(true);
        PauseHooks.afterBakjiPointsRead = this::pauseOnce;
    }

    private void pauseOnce() {
        if (!pauseArmed.compareAndSet(true, false)) {
            return;
        }
        paused.countDown();
        try {
            assertThat(resume.await(60, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void awaitPaused() throws InterruptedException {
        assertThat(paused.await(10, TimeUnit.SECONDS)).isTrue();
    }

    private Throwable runInMiddle(Callable<?> middle, Future<?> pausedWork) throws Exception {
        Future<?> middleWork = executor.submit(middle);
        try {
            middleWork.get(MIDDLE_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
        }
        resume.countDown();
        middleWork.get(60, TimeUnit.SECONDS);
        try {
            pausedWork.get(60, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        }
    }

    private static BakjiUpdateCommand rename(String name) {
        return new BakjiUpdateCommand(
                PatchField.of(name),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent());
    }

    private static BakjiUpdateCommand describe(String description) {
        return new BakjiUpdateCommand(
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.of(description),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent());
    }

    private static BakjiUpdateCommand moveTo(double lng) {
        return new BakjiUpdateCommand(
                PatchField.absent(),
                PatchField.of(LAT),
                PatchField.of(lng),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent());
    }

    private boolean warningOf(long spotId) {
        return jdbc.queryForObject("SELECT park_warning FROM spot WHERE id = ?", Boolean.class, spotId);
    }

    private Long protectedAreaIdOf(long spotId) {
        return jdbc.queryForObject("SELECT protected_area_id FROM spot WHERE id = ?", Long.class, spotId);
    }

    private String nameOf(long spotId) {
        return jdbc.queryForObject("SELECT name FROM spot WHERE id = ?", String.class, spotId);
    }

    private String statusOf(long spotId) {
        return jdbc.queryForObject("SELECT status FROM spot WHERE id = ?", String.class, spotId);
    }

    private String descriptionOf(long spotId) {
        return jdbc.queryForObject("SELECT description FROM bakji_detail WHERE spot_id = ?", String.class, spotId);
    }

    private double lngOf(long spotId) {
        return jdbc.queryForObject("SELECT ST_Longitude(location) FROM spot WHERE id = ?", Double.class, spotId);
    }

    static final class PauseHooks {

        static volatile Runnable beforeBakjiDetailRead;
        static volatile Runnable afterBakjiPointsRead;

        private PauseHooks() {}

        static void clear() {
            beforeBakjiDetailRead = null;
            afterBakjiPointsRead = null;
        }

        private static void run(Runnable hook) {
            if (hook != null) {
                hook.run();
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PauseHookConfig {

        @Bean
        static BeanPostProcessor pauseHookPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof BakjiDetailRepository) {
                        return wrap(bean, "findById", true);
                    }
                    if (bean instanceof ParkAreaJudgeMapper) {
                        return wrap(bean, "selectBakjiPoints", false);
                    }
                    return bean;
                }
            };
        }

        private static Object wrap(Object target, String methodName, boolean before) {
            return Proxy.newProxyInstance(
                    target.getClass().getClassLoader(), target.getClass().getInterfaces(), (proxy, method, args) -> {
                        boolean hooked = method.getName().equals(methodName);
                        if (hooked && before) {
                            PauseHooks.run(PauseHooks.beforeBakjiDetailRead);
                        }
                        Object result;
                        try {
                            result = method.invoke(target, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                        if (hooked && !before) {
                            PauseHooks.run(PauseHooks.afterBakjiPointsRead);
                        }
                        return result;
                    });
        }
    }
}
