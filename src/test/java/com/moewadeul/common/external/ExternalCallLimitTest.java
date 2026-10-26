package com.moewadeul.common.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExternalCallLimitTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ExternalCallLimit limit = new ExternalCallLimit(
            "kma", new ExternalApiProperties(2, Duration.ZERO), new ExternalApiMetrics(meterRegistry));

    @Test
    @DisplayName("[NFR-05] 자리가 모두 차 있으면 기다리지 않고 바로 거부하고, 거부한 수를 지표에 남긴다")
    void rejectsImmediatelyWhenFull() throws Exception {
        // given: 두 호출이 자리를 쥔 채 끝나지 않는다.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try {
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> limit.call(
                        () -> {
                            entered.countDown();
                            return awaitQuietly(release);
                        },
                        IllegalStateException::new));
            }
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            // when & then
            assertThatThrownBy(() -> limit.call(() -> "called", () -> new IllegalStateException("rejected")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("rejected");
            assertThat(meterRegistry
                            .get("moewadeul.external.api.rejected")
                            .tag("api", "kma")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
        } finally {
            release.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("[NFR-05] 호출이 끝나면 정상이든 예외든 자리를 돌려준다")
    void releasesPermitAfterSuccessAndFailure() {
        // when: 한도(2)보다 많은 호출을 차례로 보낸다. 자리를 돌려주지 않으면 세 번째부터 거부된다.
        for (int i = 0; i < 3; i++) {
            assertThat(limit.call(() -> "ok", IllegalStateException::new)).isEqualTo("ok");
        }
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> limit.call(
                            () -> {
                                throw new UnsupportedOperationException("failed");
                            },
                            IllegalStateException::new))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        // then
        assertThat(limit.call(() -> "still ok", IllegalStateException::new)).isEqualTo("still ok");
    }

    @Test
    @DisplayName("[NFR-05] 동시에 들어온 호출 중 한도만큼만 실행하고 나머지는 거부한다")
    void runsOnlyUpToLimitUnderContention() throws Exception {
        // given
        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return limit.call(() -> awaitQuietly(release), IllegalStateException::new);
                    } catch (IllegalStateException rejected) {
                        return false;
                    }
                }));
            }

            // when: 모든 호출이 시도를 마칠 때까지(거부된 호출이 끝날 때까지) 기다린 뒤 자리를 쥔 호출을 놓아 준다.
            ready.await();
            start.countDown();
            long rejected = 0;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (rejected < threads - 2 && System.nanoTime() < deadline) {
                rejected = futures.stream().filter(Future::isDone).count();
                Thread.sleep(10);
            }
            release.countDown();

            // then
            long executed = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(5, TimeUnit.SECONDS)) {
                    executed++;
                }
            }
            assertThat(executed).isEqualTo(2);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static boolean awaitQuietly(CountDownLatch latch) {
        try {
            return latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
