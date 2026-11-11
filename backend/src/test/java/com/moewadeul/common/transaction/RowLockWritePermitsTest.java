package com.moewadeul.common.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RowLockWritePermitsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RowLockWritePermits permits = new RowLockWritePermits(registry, Duration.ofMillis(50));

    @Test
    @DisplayName("[NFR-09] 허가가 모두 쓰이는 동안 호출은 RESOURCE_BUSY로 거절되고, 거절 수와 사용 수 지표에 그대로 나온다")
    void rejectionAndInUseAreVisibleInMetrics() throws Exception {
        // given
        int held = RowLockWritePermits.MAX_CONCURRENT_WRITES;
        CountDownLatch acquired = new CountDownLatch(held);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService holders = Executors.newFixedThreadPool(held);
        try {
            for (int i = 0; i < held; i++) {
                holders.submit(() -> permits.run(() -> {
                    acquired.countDown();
                    awaitQuietly(release);
                }));
            }
            assertThat(acquired.await(10, TimeUnit.SECONDS)).isTrue();

            // when
            Throwable thrown = catchThrowable(() -> permits.run(() -> {}));

            // then
            assertThat(thrown)
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.RESOURCE_BUSY));
            assertThat(inUse()).isEqualTo(held);
            assertThat(rejectedCount()).isEqualTo(1);
            assertThat(waitCount("rejected")).isEqualTo(1);
        } finally {
            release.countDown();
            holders.shutdown();
            assertThat(holders.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(inUse()).isZero();
        assertThat(waitCount("acquired")).isEqualTo(held);
    }

    @Test
    @DisplayName("[NFR-09] 허가를 바로 얻으면 거절 수는 늘지 않고 대기 시간만 acquired로 기록된다")
    void acquiredCallDoesNotCountAsRejected() {
        // when
        String result = permits.call(() -> "ok");

        // then
        assertThat(result).isEqualTo("ok");
        assertThat(rejectedCount()).isZero();
        assertThat(waitCount("acquired")).isEqualTo(1);
        assertThat(waitCount("rejected")).isZero();
        assertThat(inUse()).isZero();
    }

    @Test
    @DisplayName("[NFR-09] 허가를 기다리다 끊기면 거절로 세고 인터럽트 표시는 남긴다")
    void interruptedWaitCountsAsRejected() {
        // given
        Thread.currentThread().interrupt();

        // when / then
        try {
            assertThatThrownBy(() -> permits.run(() -> {})).isInstanceOf(BusinessException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        assertThat(rejectedCount()).isEqualTo(1);
    }

    private double inUse() {
        return registry.get("moewadeul.row.lock.writes.in.use").gauge().value();
    }

    private double rejectedCount() {
        return registry.get("moewadeul.row.lock.writes.rejected").counter().count();
    }

    private long waitCount(String outcome) {
        return registry.get("moewadeul.row.lock.writes.permit.wait")
                .tag("outcome", outcome)
                .timer()
                .count();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
