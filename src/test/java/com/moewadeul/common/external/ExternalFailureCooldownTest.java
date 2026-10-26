package com.moewadeul.common.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.testsupport.MutableClock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExternalFailureCooldownTest {

    private static final Duration COOLDOWN = Duration.ofSeconds(30);

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final MutableClock clock = MutableClock.atDefaultInstant();
    private final ExternalFailureCooldown cooldown = new ExternalFailureCooldown(
            "kma", new ExternalApiProperties(10, COOLDOWN), clock, new ExternalApiMetrics(meterRegistry));
    private final AtomicInteger calls = new AtomicInteger();

    @Test
    @DisplayName("[NFR-05] 다시 보내도 응답하지 못한 호출이 있으면, 쉬는 동안의 호출은 외부 API를 부르지 않고 건너뛴 수를 지표에 남긴다")
    void skipsCallsWhileResting() {
        // given
        triggerOutage();

        // when & then
        assertThatThrownBy(this::callOk)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("skipped");
        assertThatThrownBy(this::callOk)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("skipped");
        assertThat(calls).hasValue(1);
        assertThat(meterRegistry
                        .get("moewadeul.external.api.skipped")
                        .tag("api", "kma")
                        .counter()
                        .count())
                .isEqualTo(2.0);
    }

    @Test
    @DisplayName("[NFR-05] 쉬는 시간이 지나기 직전까지는 계속 건너뛰고, 지나면 외부 API를 다시 부른다")
    void callsAgainOnlyAfterCooldown() {
        // given
        triggerOutage();

        // when & then
        clock.advance(COOLDOWN.minusMillis(1));
        assertThatThrownBy(this::callOk).hasMessage("skipped");
        clock.advance(Duration.ofMillis(1));
        assertThat(callOk()).isEqualTo("ok");
        assertThat(calls).hasValue(2);
    }

    @Test
    @DisplayName("[NFR-05] 시험 호출이 성공하면 바로 정상으로 돌아오고, 실패하면 다시 쉬는 시간만큼 쉰다")
    void probeDecidesNextState() {
        // given
        triggerOutage();
        clock.advance(COOLDOWN);

        // when: 시험 호출이 또 실패한다.
        assertThatThrownBy(this::failOutage).isInstanceOf(OutageException.class);

        // then: 다시 쉰다. 쉬는 시간이 지난 뒤 시험 호출이 성공하면 이어지는 호출은 모두 외부 API로 나간다.
        assertThatThrownBy(this::callOk).hasMessage("skipped");
        clock.advance(COOLDOWN);
        assertThat(callOk()).isEqualTo("ok");
        assertThat(callOk()).isEqualTo("ok");
        assertThat(calls).hasValue(4);
    }

    @Test
    @DisplayName("[NFR-05] 외부 API가 오류 코드로 답한 호출은 API가 살아 있다는 뜻이라서 쉬지 않는다")
    void doesNotRestWhenFailureIsNotOutage() {
        // when
        assertThatThrownBy(() -> cooldown.call(
                        () -> {
                            calls.incrementAndGet();
                            throw new IllegalArgumentException("resultCode=10");
                        },
                        e -> e instanceof OutageException,
                        () -> new IllegalStateException("skipped")))
                .isInstanceOf(IllegalArgumentException.class);

        // then
        assertThat(callOk()).isEqualTo("ok");
        assertThat(calls).hasValue(2);
    }

    @Test
    @DisplayName("[NFR-05] 쉬는 시간이 지났을 때 동시에 들어온 호출 중 하나만 외부 API를 부른다")
    void onlyOneCallProbesAfterCooldown() throws Exception {
        // given: 쉬는 시간이 막 지났고 여러 스레드가 동시에 호출한다. 시험 호출은 끝나지 않는다.
        triggerOutage();
        clock.advance(COOLDOWN);
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch probeEntered = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return cooldown.call(
                                () -> {
                                    calls.incrementAndGet();
                                    probeEntered.countDown();
                                    awaitQuietly(release);
                                    return "ok";
                                },
                                e -> e instanceof OutageException,
                                () -> new IllegalStateException("skipped"));
                    } catch (IllegalStateException skipped) {
                        return "skipped";
                    }
                }));
            }

            // when: 시험 호출이 안에 들어간 뒤, 나머지 호출이 모두 건너뛰기를 마칠 때까지 기다린 뒤 시험 호출을 놓아 준다.
            ready.await();
            start.countDown();
            assertThat(probeEntered.await(5, TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (futures.stream().filter(Future::isDone).count() < threads - 1 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            release.countDown();

            // then
            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) {
                outcomes.add(future.get(5, TimeUnit.SECONDS));
            }
            assertThat(outcomes.stream().filter("ok"::equals).count()).isEqualTo(1);
            assertThat(outcomes.stream().filter("skipped"::equals).count()).isEqualTo(threads - 1);
            assertThat(calls).hasValue(2);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("[NFR-05] 쉬는 시간을 0으로 두면 실패가 이어져도 쉬지 않는다")
    void neverRestsWhenCooldownIsZero() {
        // given
        ExternalFailureCooldown disabled = new ExternalFailureCooldown(
                "kma", new ExternalApiProperties(10, Duration.ZERO), clock, new ExternalApiMetrics(meterRegistry));

        // when
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> disabled.call(
                            () -> {
                                calls.incrementAndGet();
                                throw new OutageException();
                            },
                            e -> e instanceof OutageException,
                            () -> new IllegalStateException("skipped")))
                    .isInstanceOf(OutageException.class);
        }

        // then
        assertThat(calls).hasValue(3);
    }

    private String callOk() {
        return cooldown.call(
                () -> {
                    calls.incrementAndGet();
                    return "ok";
                },
                e -> e instanceof OutageException,
                () -> new IllegalStateException("skipped"));
    }

    private void triggerOutage() {
        assertThatThrownBy(this::failOutage).isInstanceOf(OutageException.class);
    }

    private String failOutage() {
        return cooldown.call(
                () -> {
                    calls.incrementAndGet();
                    throw new OutageException();
                },
                e -> e instanceof OutageException,
                () -> new IllegalStateException("skipped"));
    }

    private static boolean awaitQuietly(CountDownLatch latch) {
        try {
            return latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 응답하지 못하는 상태(I/O 오류, 제한 시간 초과, 5xx)를 흉내 내는 예외다. */
    private static final class OutageException extends RuntimeException {}
}
