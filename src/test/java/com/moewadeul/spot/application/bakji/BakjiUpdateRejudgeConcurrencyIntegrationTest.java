package com.moewadeul.spot.application.bakji;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.common.web.PatchField;
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

/**
 * 박지 수정과 박지 재판정, 관리자 숨김이 같은 박지 행에서 겹칠 때 마지막에 남는 값을 확인한다.
 *
 * <p>동시에 출발시키는 것만으로는 원하는 순서가 잘 나오지 않는다. 그래서 테스트는 한 트랜잭션을 정해 둔 지점에서 멈추고, 그사이 다른 쪽을 실행한 뒤
 * 멈춘 트랜잭션을 이어 간다. 박지 수정은 박지 행을 읽은 직후(제보 상세를 읽을 때), 재판정은 박지 좌표를 모두 읽은 직후에 멈춘다.
 *
 * <p>가운데 작업은 다른 스레드에서 돌린다. 멈춘 트랜잭션이 행을 잠그고 있으면 가운데 작업이 그 잠금을 기다리기 때문이다. 이때 테스트는 잠깐 기다린 뒤
 * 멈춘 트랜잭션을 풀어 주고, 두 작업이 모두 끝난 뒤 값을 본다.
 */
@IntegrationTest
class BakjiUpdateRejudgeConcurrencyIntegrationTest {

    private static final int REPETITIONS = 5;
    // 공원 경계는 위도 37~38, 경도 128~129 사각형이다. 안쪽 점과 바깥 점은 위도가 같고 경도만 다르다.
    private static final String AREA_LONG_LAT_WKT = "MULTIPOLYGON(((128 37, 129 37, 129 38, 128 38, 128 37)))";
    private static final double LAT = 37.6;
    private static final double INSIDE_LNG = 128.7;
    private static final double OUTSIDE_LNG = 127.7;
    private static final long MIDDLE_WAIT_SECONDS = 2;

    @Autowired
    private BakjiCommandService bakjiCommandService;

    @Autowired
    private BakjiEditService bakjiEditService;

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
        // 처음에는 경계가 없어서 이 좌표의 박지는 경고가 아니다. 수정이 멈춘 사이 경계를 넣으면 경계 안이 된다.
        long spotId = report(INSIDE_LNG);
        pauseUpdateAfterReadingSpot();

        Future<?> update = executor.submit(() -> bakjiEditService.update(reporterId, spotId, rename("새 이름")));
        awaitPaused();
        long areaId = insertProtectedArea();
        assertThat(runInMiddle(this::rejudgeAll, update)).isNull();

        assertThat(warningOf(spotId)).isTrue();
        assertThat(protectedAreaIdOf(spotId)).isEqualTo(areaId);
        assertThat(nameOf(spotId)).isEqualTo("새 이름");
    }

    @RepeatedTest(REPETITIONS)
    @DisplayName("[F-06][F-07][BC-04] 재판정이 좌표를 읽은 뒤 박지를 공원 경계 안으로 옮기면, 옮긴 좌표의 경고가 남는다")
    void moveIntoAreaDuringRejudgeKeepsWarning() throws Exception {
        long areaId = insertProtectedArea();
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

    // 재판정 작업처럼 박지를 묶음마다 끝까지 판정한다.
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

    // 박지 수정은 박지 행을 읽은 다음 제보 상세를 읽는다. 그래서 제보 상세를 읽기 직전에 멈추면 박지 행을 읽은 직후에 멈춘다.
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

    // 가운데 작업을 다른 스레드에서 시작한다. 멈춘 트랜잭션의 잠금을 기다리느라 끝나지 않으면 잠깐 기다린 뒤 멈춘 트랜잭션을 풀어 준다.
    // 멈췄던 작업이 예외로 끝나면 그 예외를 돌려주고, 성공하면 null을 돌려준다. 가운데 작업은 성공해야 한다.
    private Throwable runInMiddle(Callable<?> middle, Future<?> pausedWork) throws Exception {
        Future<?> middleWork = executor.submit(middle);
        try {
            middleWork.get(MIDDLE_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // 가운데 작업이 잠금을 기다리는 중이다. 멈춘 쪽을 풀면 이어서 끝난다.
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

    private long insertProtectedArea() {
        jdbc.update(
                "INSERT INTO protected_area (name, area_type, source, source_date, boundary, created_at, updated_at)"
                        + " VALUES ('오대산', 'NATIONAL_PARK', 'KDPA', '2026-01-01',"
                        + " ST_GeomFromText(?, 4326, 'axis-order=long-lat'), NOW(6), NOW(6))",
                AREA_LONG_LAT_WKT);
        return jdbc.queryForObject("SELECT MAX(id) FROM protected_area", Long.class);
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

    private double lngOf(long spotId) {
        return jdbc.queryForObject("SELECT ST_Longitude(location) FROM spot WHERE id = ?", Double.class, spotId);
    }

    /** 멈출 지점에서 부를 동작이다. 테스트가 끝나면 비운다. */
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

    // 제보 상세 리포지토리와 재판정 매퍼를 한 겹 감싸서, 정해 둔 메서드의 앞이나 뒤에서 멈출 지점을 부른다.
    // 두 빈은 JDK 프록시라서 Mockito 스파이로는 실제 메서드를 부를 수 없다. 그래서 같은 인터페이스를 구현하는 프록시로 감싸 원래 빈에 넘긴다.
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
