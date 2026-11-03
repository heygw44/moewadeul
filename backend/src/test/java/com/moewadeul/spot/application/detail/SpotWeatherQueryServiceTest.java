package com.moewadeul.spot.application.detail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moewadeul.common.external.ExternalApiMetrics;
import com.moewadeul.spot.infra.detail.SpotDetailMapper;
import com.moewadeul.spot.infra.detail.SpotWeatherLocationRow;
import com.moewadeul.weather.application.SunQueryService;
import com.moewadeul.weather.application.WeatherForecast;
import com.moewadeul.weather.application.WeatherProperties;
import com.moewadeul.weather.application.WeatherQueryService;
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
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpotWeatherQueryServiceTest {

    private static final long SPOT_ID = 7L;

    private final SpotDetailMapper mapper = mock(SpotDetailMapper.class);
    private final WeatherQueryService weatherQueryService = mock(WeatherQueryService.class);
    private final SunQueryService sunQueryService = mock(SunQueryService.class);
    private final WeatherForecast forecast = new WeatherForecast("기상청", List.of(), List.of());

    SpotWeatherQueryServiceTest() {
        when(mapper.selectActiveWeatherLocation(anyLong()))
                .thenReturn(Optional.of(new SpotWeatherLocationRow(37.5, 127.0, 60, 127)));
    }

    @Test
    @DisplayName("[F-09][NFR-05] 기상청 처리에서 예상 못 한 예외가 나면 빈 값으로 감추지 않고 예외로 끝내며, 입장 자리는 돌려준다")
    void failsFutureAndReturnsPermitWhenForecastThrows() {
        // given
        when(weatherQueryService.forecast(anyInt(), anyInt(), anyDouble(), anyDouble()))
                .thenThrow(new IllegalStateException("boom"))
                .thenReturn(Optional.of(forecast));
        when(sunQueryService.sunTimes(anyDouble(), anyDouble(), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        SpotWeatherQueryService service = service(Runnable::run, 1);

        // when
        CompletableFuture<Optional<SpotWeather>> failed = service.findWeather(SPOT_ID);

        // then
        assertThatThrownBy(failed::join)
                .isInstanceOf(CompletionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(service.findWeather(SPOT_ID).join()).isPresent();
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
        CompletableFuture<Optional<SpotWeather>> failed =
                service(Runnable::run, 1).findWeather(SPOT_ID);

        // then
        assertThatThrownBy(failed::join)
                .isInstanceOf(CompletionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 실행기가 작업을 받지 않으면 예외를 그대로 던지고 입장 자리는 돌려준다")
    void rethrowsRejectionAndReturnsPermit() {
        // given
        when(weatherQueryService.forecast(anyInt(), anyInt(), anyDouble(), anyDouble()))
                .thenReturn(Optional.of(forecast));
        when(sunQueryService.sunTimes(anyDouble(), anyDouble(), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        boolean[] rejecting = {true};
        SpotWeatherQueryService service = service(
                task -> {
                    if (rejecting[0]) {
                        throw new RejectedExecutionException("closed");
                    }
                    task.run();
                },
                1);

        // when
        assertThatThrownBy(() -> service.findWeather(SPOT_ID)).isInstanceOf(RejectedExecutionException.class);
        rejecting[0] = false;

        // then
        assertThat(service.findWeather(SPOT_ID).join()).isPresent();
    }

    private SpotWeatherQueryService service(Executor executor, int maxInFlight) {
        return new SpotWeatherQueryService(
                mapper,
                weatherQueryService,
                sunQueryService,
                new ExternalApiMetrics(new SimpleMeterRegistry()),
                new WeatherProperties(Duration.ofSeconds(3), maxInFlight, 25),
                executor,
                Clock.fixed(Instant.parse("2026-10-04T03:58:00Z"), ZoneOffset.UTC));
    }
}
