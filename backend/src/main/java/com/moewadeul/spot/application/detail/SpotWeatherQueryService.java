package com.moewadeul.spot.application.detail;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.external.ExternalApiMetrics;
import com.moewadeul.common.time.KoreaTime;
import com.moewadeul.spot.infra.detail.SpotDetailMapper;
import com.moewadeul.spot.infra.detail.SpotWeatherLocationRow;
import com.moewadeul.weather.application.SunQueryService;
import com.moewadeul.weather.application.SunTimes;
import com.moewadeul.weather.application.WeatherExecutorConfig;
import com.moewadeul.weather.application.WeatherForecast;
import com.moewadeul.weather.application.WeatherProperties;
import com.moewadeul.weather.application.WeatherQueryService;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SpotWeatherQueryService {

    private static final String KMA = "kma";
    private static final String KASI = "kasi";

    private final SpotDetailMapper spotDetailMapper;
    private final WeatherQueryService weatherQueryService;
    private final SunQueryService sunQueryService;
    private final ExternalApiMetrics metrics;
    private final WeatherProperties properties;
    private final Executor weatherExecutor;
    private final Clock clock;
    private final Semaphore admission;

    public SpotWeatherQueryService(
            SpotDetailMapper spotDetailMapper,
            WeatherQueryService weatherQueryService,
            SunQueryService sunQueryService,
            ExternalApiMetrics metrics,
            WeatherProperties properties,
            @Qualifier(WeatherExecutorConfig.WEATHER_EXECUTOR) Executor weatherExecutor,
            Clock clock) {
        this.spotDetailMapper = spotDetailMapper;
        this.weatherQueryService = weatherQueryService;
        this.sunQueryService = sunQueryService;
        this.metrics = metrics;
        this.properties = properties;
        this.weatherExecutor = weatherExecutor;
        this.clock = clock;
        this.admission = new Semaphore(properties.maxInFlight());
    }

    public CompletableFuture<Optional<SpotWeather>> findWeather(long spotId) {
        SpotWeatherLocationRow location =
                spotDetailMapper.selectActiveWeatherLocation(spotId).orElseThrow(BusinessException::notFound);
        if (!admission.tryAcquire()) {
            metrics.recordWeatherAdmissionRejected();
            return CompletableFuture.completedFuture(Optional.empty());
        }
        long startedAt = System.nanoTime();
        CompletableFuture<Optional<WeatherForecast>> forecast = new CompletableFuture<>();
        CompletableFuture<Optional<SunTimes>> sun = new CompletableFuture<>();
        Executor traced = withCallerMdc(weatherExecutor);
        try {
            traced.execute(() -> fetchThenReleaseAdmission(location, forecast, sun));
        } catch (RejectedExecutionException e) {
            admission.release();
            throw e;
        }
        return forecast.orTimeout(properties.responseDeadline().toNanos(), TimeUnit.NANOSECONDS)
                .exceptionallyAsync(e -> onDeadline(e, KMA, "기상청", "날씨"), traced)
                .thenCompose(found -> found.isEmpty()
                        ? CompletableFuture.completedFuture(Optional.<SpotWeather>empty())
                        : sun.orTimeout(remaining(startedAt), TimeUnit.NANOSECONDS)
                                .exceptionallyAsync(e -> onDeadline(e, KASI, "천문연", "출몰시각"), traced)
                                .thenApply(times -> Optional.of(new SpotWeather(found.get(), times.orElse(null)))));
    }

    private void fetchThenReleaseAdmission(
            SpotWeatherLocationRow location,
            CompletableFuture<Optional<WeatherForecast>> forecast,
            CompletableFuture<Optional<SunTimes>> sun) {
        try {
            Optional<WeatherForecast> found = weatherQueryService.forecast(
                    location.weatherNx(), location.weatherNy(), location.lat(), location.lng());
            boolean responseStillWaiting = forecast.complete(found);
            if (responseStillWaiting && found.isPresent()) {
                sun.complete(sunQueryService.sunTimes(location.lat(), location.lng(), KoreaTime.today(clock)));
            }
        } catch (Throwable e) {
            forecast.completeExceptionally(e);
            sun.completeExceptionally(e);
        } finally {
            admission.release();
        }
    }

    private long remaining(long startedAt) {
        return Math.max(0, properties.responseDeadline().toNanos() - (System.nanoTime() - startedAt));
    }

    // 시간 초과 이어받기는 JDK 공용 스케줄러 스레드에서 돈다. 로그가 그 스레드를 막지 않게 호출한 쪽이 실행기로 넘긴다.
    private <T> Optional<T> onDeadline(Throwable failure, String api, String apiName, String missing) {
        if (failure instanceof TimeoutException) {
            log.warn(
                    "{} 응답이 {}ms 안에 오지 않아 {} 없이 응답한다.",
                    apiName,
                    properties.responseDeadline().toMillis(),
                    missing);
            metrics.recordDeadline(api);
            return Optional.empty();
        }
        throw new CompletionException(failure);
    }

    private static Executor withCallerMdc(Executor executor) {
        Map<String, String> callerMdc = MDC.getCopyOfContextMap();
        return task -> executor.execute(() -> {
            if (callerMdc != null) {
                MDC.setContextMap(callerMdc);
            }
            task.run();
        });
    }
}
