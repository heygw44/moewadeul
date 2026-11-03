package com.moewadeul.weather.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moewadeul.common.external.ExternalApiMetrics;
import com.moewadeul.weather.infra.KasiClient;
import com.moewadeul.weather.infra.KasiRiseSetItem;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SunQueryServiceTest {

    private final KasiClient kasiClient = mock(KasiClient.class);
    private final SunQueryService service = new SunQueryService(kasiClient, null, null);

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 진행 중인 천문연 호출을 기다릴 자리가 없으면 예외 없이 출몰시각 없이 응답하고, 거부를 지표에 남긴다")
    void returnsEmptyWithoutWaitingWhenNoWaiterSlotIsLeft() throws Exception {
        // given
        LocalDate date = LocalDate.of(2026, 10, 5);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        SunQueryService busy = new SunQueryService(
                kasiClient, new WeatherCaches(meterRegistry), new ExternalApiMetrics(meterRegistry));
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(kasiClient.getRiseSet(any(), anyDouble(), anyDouble())).thenAnswer(invocation -> {
            loading.countDown();
            release.await(5, TimeUnit.SECONDS);
            return Optional.<KasiRiseSetItem>empty();
        });
        ExecutorService executor = Executors.newFixedThreadPool(WeatherCaches.MAX_WAITERS + 1);
        try {
            List<Future<Optional<SunTimes>>> inFlight = new ArrayList<>();
            inFlight.add(executor.submit(() -> busy.sunTimes(37.5, 127.0, date)));
            assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
            List<Thread> waiters = new CopyOnWriteArrayList<>();
            for (int i = 0; i < WeatherCaches.MAX_WAITERS; i++) {
                inFlight.add(executor.submit(() -> {
                    waiters.add(Thread.currentThread());
                    return busy.sunTimes(37.5, 127.0, date);
                }));
            }
            await().atMost(Duration.ofSeconds(5))
                    .until(() -> waiters.size() == WeatherCaches.MAX_WAITERS
                            && waiters.stream().allMatch(thread -> thread.getState() == Thread.State.WAITING));

            // when
            Optional<SunTimes> rejected = busy.sunTimes(37.5, 127.0, date);

            // then
            assertThat(rejected).isEmpty();
            assertThat(inFlight).noneMatch(Future::isDone);
            assertThat(meterRegistry
                            .get("moewadeul.external.api.rejected")
                            .tag("api", "kasi")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("일출·일몰 시각을 트랜잭션 안에서 부르면 천문연에 묻기 전에 거부한다")
    void rejectsTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(() -> service.sunTimes(37.5, 127.0, LocalDate.of(2026, 10, 5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("트랜잭션 밖에서 불러야 합니다.");
        verifyNoInteractions(kasiClient);
    }
}
