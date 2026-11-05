package com.moewadeul.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moewadeul.common.metrics.PolledSnapshot;
import com.moewadeul.common.testsupport.MutableClock;
import com.moewadeul.outbox.infra.OutboxBacklogRow;
import com.moewadeul.outbox.infra.OutboxEventMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxMetricsTest {

    private final MutableClock clock = MutableClock.atDefaultInstant();
    private final OutboxEventMapper mapper = mock(OutboxEventMapper.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final OutboxMetrics metrics = new OutboxMetrics(mapper, clock);

    OutboxMetricsTest() {
        metrics.bindTo(registry);
    }

    @Test
    @DisplayName("[NFR-09] 수집 중에 게이지는 DB를 읽지 않아서, 마지막 읽기가 커넥션을 기다리며 막혀 있어도 읽어 둔 값을 바로 돌려준다")
    void gaugeDoesNotWaitWhileRefreshIsBlocked() throws Exception {
        when(mapper.selectBacklog())
                .thenReturn(new OutboxBacklogRow(3, 1, clock.instant().minusSeconds(120)));
        metrics.refresh();
        CountDownLatch refreshEntered = new CountDownLatch(1);
        CountDownLatch poolFreed = new CountDownLatch(1);
        when(mapper.selectBacklog()).thenAnswer(invocation -> {
            refreshEntered.countDown();
            poolFreed.await();
            return new OutboxBacklogRow(0, 0, null);
        });
        CompletableFuture<Void> blockedRefresh = CompletableFuture.runAsync(metrics::refresh);
        assertThat(refreshEntered.await(5, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Double> collected = CompletableFuture.supplyAsync(() -> events("pending"));

        assertThat(collected.get(2, TimeUnit.SECONDS)).isEqualTo(3.0);
        poolFreed.countDown();
        blockedRefresh.get(5, TimeUnit.SECONDS);
        assertThat(events("pending")).isZero();
    }

    @Test
    @DisplayName("[NFR-09] 읽어 둔 나이는 읽은 시각 기준이다. 다음 읽기 전에는 자라지 않아서 이미 발행된 이벤트의 나이를 부풀려 경보를 울리지 않는다")
    void ageIsAsOfLastRefresh() {
        when(mapper.selectBacklog())
                .thenReturn(new OutboxBacklogRow(1, 0, clock.instant().minusSeconds(590)));
        metrics.refresh();

        clock.advance(Duration.ofSeconds(30));

        assertThat(oldestPendingAgeSeconds()).isEqualTo(590.0);
    }

    @Test
    @DisplayName("[NFR-09] 읽기가 계속 실패해 STALE_AFTER가 지나면 낡은 값을 정상으로 보이게 하지 않고 모든 게이지가 NaN이다")
    void gaugesTurnNaNWhenRefreshKeepsFailing() {
        when(mapper.selectBacklog())
                .thenReturn(new OutboxBacklogRow(2, 1, clock.instant().minusSeconds(700)));
        metrics.refresh();
        when(mapper.selectBacklog()).thenThrow(new IllegalStateException("커넥션을 얻지 못했다"));

        clock.advance(PolledSnapshot.STALE_AFTER.plusSeconds(1));
        assertThatThrownBy(metrics::refresh).isInstanceOf(IllegalStateException.class);

        assertThat(events("pending")).isNaN();
        assertThat(events("failed")).isNaN();
        assertThat(oldestPendingAgeSeconds()).isNaN();
    }

    @Test
    @DisplayName("[NFR-09] 앱을 막 띄워 아직 읽은 적이 없으면 0이 아니라 NaN이다")
    void gaugesAreNaNBeforeFirstRefresh() {
        assertThat(events("pending")).isNaN();
        assertThat(oldestPendingAgeSeconds()).isNaN();
    }

    private double events(String status) {
        return registry.get("moewadeul.outbox.events")
                .tag("status", status)
                .gauge()
                .value();
    }

    private double oldestPendingAgeSeconds() {
        return registry.get("moewadeul.outbox.oldest.pending.age").gauge().value();
    }
}
