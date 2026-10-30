package com.moewadeul.program.application.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.member.infra.MemberJpaRepository;
import com.moewadeul.program.application.ProgramApplyFixture;
import com.moewadeul.program.domain.ProgramApplicationRepository;
import com.moewadeul.program.domain.ProgramErrorCode;
import com.moewadeul.program.domain.ProgramRepository;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ProgramVacancyAlertConcurrencyIntegrationTest {

    private static final int CAPACITY = 1;

    @Autowired
    private ProgramVacancyAlertService vacancyAlertService;

    @Autowired
    private ProgramPaymentService paymentService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private final CountDownLatch paused = new CountDownLatch(1);
    private final CountDownLatch resume = new CountDownLatch(1);
    private final AtomicBoolean pauseArmed = new AtomicBoolean();
    private ExecutorService executor;
    private ProgramApplyFixture fixture;
    private long programId;
    private long holderId;
    private long holderApplicationId;
    private long subscriberId;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
        programId = fixture.saveProgram(CAPACITY, false);
        holderId = fixture.saveMember();
        holderApplicationId = fixture.saveApplication(programId, holderId, "PENDING_PAYMENT");
        subscriberId = fixture.saveMember();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        PauseHooks.clear();
        resume.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("[F-20][PG-07] 알림 신청이 정원이 찼다고 본 뒤 신청자가 취소해 커밋해도, 신청은 통보 없이 남지 않는다")
    void cancelCommitsAfterSubscriberSawFullProgram() throws Exception {
        pauseAfter(Set.of("ProgramApplicationRepository.countSeatHolders"));
        Future<Boolean> subscribe = executor.submit(() -> vacancyAlertService.subscribe(subscriberId, programId));
        awaitPaused();

        paymentService.cancel(holderId, holderApplicationId);
        resume.countDown();
        subscribeOutcome(subscribe);

        assertAlertNotStranded();
    }

    @Test
    @DisplayName("[F-20][PG-07] 취소가 알림 대상을 정하기 전에 알림 신청이 끝나도, 신청자는 빈자리 알림을 받는다")
    void subscribeCommitsBeforeCancelPicksTargets() throws Exception {
        pauseAfter(Set.of("ProgramRepository.findById"));
        Future<?> cancel = executor.submit(() -> paymentService.cancel(holderId, holderApplicationId));
        awaitPaused();

        boolean created = vacancyAlertService.subscribe(subscriberId, programId);
        resume.countDown();
        cancel.get(60, TimeUnit.SECONDS);

        assertThat(created).isTrue();
        assertAlertNotStranded();
    }

    private void assertAlertNotStranded() {
        int alerts = fixture.vacancyAlertCount(programId, subscriberId);
        LocalDateTime notifiedAt = alerts == 1 ? fixture.vacancyAlertNotifiedAt(programId, subscriberId) : null;
        int freeSeats = CAPACITY - fixture.activeCount(programId);

        assertThat(alerts == 1 && notifiedAt == null && freeSeats > 0)
                .as("빈자리 %d개인데 알림 신청 %d건이 알린 적 없이 남았다", freeSeats, alerts)
                .isFalse();
    }

    private void subscribeOutcome(Future<Boolean> subscribe) throws Exception {
        try {
            subscribe.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) e.getCause()).getErrorCode())
                    .isEqualTo(ProgramErrorCode.PROGRAM_SEATS_AVAILABLE);
        }
    }

    private void pauseAfter(Set<String> calls) {
        pauseArmed.set(true);
        PauseHooks.afterCalls = calls;
        PauseHooks.action = this::pauseOnce;
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

    static final class PauseHooks {

        static volatile Set<String> afterCalls = Set.of();
        static volatile Runnable action;

        private PauseHooks() {}

        static void clear() {
            afterCalls = Set.of();
            action = null;
        }

        private static void after(String call) {
            Runnable hook = action;
            if (hook != null && afterCalls.contains(call)) {
                hook.run();
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PauseHookConfig {

        @Bean
        static BeanPostProcessor programPauseHookPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof ProgramApplicationRepository) {
                        return wrap(bean, "ProgramApplicationRepository");
                    }
                    if (bean instanceof ProgramRepository) {
                        return wrap(bean, "ProgramRepository");
                    }
                    return bean;
                }
            };
        }

        private static Object wrap(Object target, String name) {
            return Proxy.newProxyInstance(
                    target.getClass().getClassLoader(), target.getClass().getInterfaces(), (proxy, method, args) -> {
                        Object result;
                        try {
                            result = method.invoke(target, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                        PauseHooks.after(name + "." + method.getName());
                        return result;
                    });
        }
    }
}
