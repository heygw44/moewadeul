package com.moewadeul.weather.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.util.concurrent.Executor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    private LocationWeatherService service(Executor executor, int maxInFlight) {
        return new LocationWeatherService(
                weatherQueryService,
                sunQueryService,
                new ExternalApiMetrics(new SimpleMeterRegistry()),
                new WeatherProperties(Duration.ofSeconds(3), maxInFlight),
                executor,
                Clock.fixed(Instant.parse("2026-10-04T03:58:00Z"), ZoneOffset.UTC));
    }
}
