package com.moewadeul.weather.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moewadeul.common.external.ExternalApiMetrics;
import com.moewadeul.weather.infra.KmaApiException;
import com.moewadeul.weather.infra.KmaClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
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

class WeatherQueryServiceTest {

    private final KmaClient kmaClient = mock(KmaClient.class);
    private final WeatherQueryService service = new WeatherQueryService(kmaClient, null, null, null, Clock.systemUTC());

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 진행 중인 기상청 호출을 기다릴 자리가 없으면 예외 없이 날씨 없이 응답하고, 자리가 없었던 수만 따로 지표에 남긴다")
    void returnsEmptyWithoutWaitingWhenNoWaiterSlotIsLeft() throws Exception {
        // given
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        WeatherQueryService busy = new WeatherQueryService(
                kmaClient,
                new WeatherCaches(meterRegistry),
                null,
                new ExternalApiMetrics(meterRegistry),
                Clock.systemUTC());
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(kmaClient.getVilageFcst(any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            loading.countDown();
            release.await(5, TimeUnit.SECONDS);
            throw new KmaApiException("기상청 응답 실패");
        });
        ExecutorService executor = Executors.newFixedThreadPool(WeatherCaches.MAX_WAITERS + 1);
        try {
            List<Future<Optional<WeatherForecast>>> inFlight = new ArrayList<>();
            inFlight.add(executor.submit(() -> busy.forecast(60, 127, 37.5, 127.0)));
            assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
            List<Thread> waiters = new CopyOnWriteArrayList<>();
            for (int i = 0; i < WeatherCaches.MAX_WAITERS; i++) {
                inFlight.add(executor.submit(() -> {
                    waiters.add(Thread.currentThread());
                    return busy.forecast(60, 127, 37.5, 127.0);
                }));
            }
            await().atMost(Duration.ofSeconds(5))
                    .until(() -> waiters.size() == WeatherCaches.MAX_WAITERS
                            && waiters.stream().allMatch(thread -> thread.getState() == Thread.State.WAITING));

            // when
            Optional<WeatherForecast> rejected = busy.forecast(60, 127, 37.5, 127.0);

            // then
            assertThat(rejected).isEmpty();
            assertThat(inFlight).noneMatch(Future::isDone);
            assertThat(meterRegistry
                            .get("moewadeul.weather.cache.busy")
                            .tag("api", "kma")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
            assertThat(meterRegistry.find("moewadeul.external.api.rejected").counter())
                    .isNull();
            release.countDown();
            for (Future<Optional<WeatherForecast>> result : inFlight) {
                assertThat(result.get(5, TimeUnit.SECONDS)).isEmpty();
            }
            assertThat(meterRegistry
                            .get("moewadeul.external.api.fallbacks")
                            .tag("api", "kma")
                            .counter()
                            .count())
                    .isEqualTo(WeatherCaches.MAX_WAITERS + 1.0);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("기상청 예보를 트랜잭션 안에서 부르면 기상청에 묻기 전에 거부한다")
    void rejectsTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(() -> service.forecast(60, 127, 37.5, 127.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("트랜잭션 밖에서 불러야 합니다.");
        verifyNoInteractions(kmaClient);
    }
}
