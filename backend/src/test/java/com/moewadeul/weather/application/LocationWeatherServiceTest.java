package com.moewadeul.weather.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.moewadeul.common.external.ExternalApiMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LocationWeatherServiceTest {

    private final WeatherQueryService weatherQueryService = mock(WeatherQueryService.class);
    private final SunQueryService sunQueryService = mock(SunQueryService.class);
    private final WeatherForecast forecast = new WeatherForecast("기상청", List.of(), List.of());

    @Test
    @DisplayName("[F-09][NFR-05] 기상청 처리에서 예상 못 한 예외가 나면 빈 값으로 감추지 않고 예외로 끝내며, 입장 자리는 돌려준다")
    void failsFutureAndReturnsPermitWhenForecastThrows() {
        // given
        when(weatherQueryService.forecast(anyInt(), anyInt(), anyDouble(), anyDouble()))
                .thenThrow(new IllegalStateException("boom"))
                .thenReturn(Optional.of(forecast));
        when(sunQueryService.sunTimes(anyDouble(), anyDouble(), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        LocationWeatherService service = service(Runnable::run, 1);

        // when
        CompletableFuture<Optional<LocationWeather>> failed = service.findWeather(60, 127, 37.5, 127.0);

        // then
        assertThatThrownBy(failed::join)
                .isInstanceOf(CompletionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(service.findWeather(60, 127, 37.5, 127.0).join()).isPresent();
    }

    @Test
    @DisplayName("[F-09][NFR-05] 천문연 처리에서 예상 못 한 예외가 나면 sun만 null로 응답하지 않고 예외로 끝낸다")
    void failsFutureWhenSunLookupThrows() {
        // given
        when(weatherQueryService.forecast(anyInt(), anyInt(), anyDouble(), anyDouble()))
                .thenReturn(Optional.of(forecast));
        when(sunQueryService.sunTimes(anyDouble(), anyDouble(), any(LocalDate.class)))
                .thenThrow(new IllegalStateException("boom"));

        // when
        CompletableFuture<Optional<LocationWeather>> failed =
                service(Runnable::run, 1).findWeather(60, 127, 37.5, 127.0);

        // then
        assertThatThrownBy(failed::join)
                .isInstanceOf(CompletionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 응답 제한 시간이 지난 뒤에 난 실패도 오류 로그로 남긴다")
    void logsFailureThatArrivesAfterDeadline() throws Exception {
        // given
        Logger logger = (Logger) LoggerFactory.getLogger(LocationWeatherService.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        CountDownLatch pastDeadline = new CountDownLatch(1);
        when(weatherQueryService.forecast(anyInt(), anyInt(), anyDouble(), anyDouble()))
                .thenAnswer(invocation -> {
                    pastDeadline.await();
                    throw new IllegalStateException("late boom");
                });
        LocationWeatherService service = service(task -> Thread.ofVirtual().start(task), 1, Duration.ofMillis(50));

        try {
            // when
            Optional<LocationWeather> answered =
                    service.findWeather(60, 127, 37.5, 127.0).join();
            pastDeadline.countDown();

            // then
            assertThat(answered).isEmpty();
            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(List.copyOf(logs.list)).anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("late boom");
                    }));
        } finally {
            logger.detachAppender(logs);
        }
    }

    private LocationWeatherService service(Executor executor, int maxInFlight) {
        return service(executor, maxInFlight, Duration.ofSeconds(3));
    }

    private LocationWeatherService service(Executor executor, int maxInFlight, Duration deadline) {
        return new LocationWeatherService(
                weatherQueryService,
                sunQueryService,
                new ExternalApiMetrics(new SimpleMeterRegistry()),
                new WeatherProperties(deadline, maxInFlight),
                executor,
                Clock.fixed(Instant.parse("2026-10-04T03:58:00Z"), ZoneOffset.UTC));
    }
}
