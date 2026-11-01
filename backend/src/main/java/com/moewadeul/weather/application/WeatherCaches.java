package com.moewadeul.weather.application;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.moewadeul.weather.application.WeatherForecast.ShortTermForecast;
import com.moewadeul.weather.infra.KmaMidTermItem;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class WeatherCaches {

    private static final Duration SHORT_TERM_TTL = Duration.ofHours(3);
    private static final Duration MID_TERM_TTL = Duration.ofHours(12);
    private static final Duration SUN_TIMES_TTL = Duration.ofHours(24);
    private static final long SHORT_TERM_MAX = 2_000;
    private static final long MID_TERM_MAX = 500;
    private static final long SUN_TIMES_MAX = 10_000;

    private final AsyncCache<ShortTermKey, List<ShortTermForecast>> shortTerm;
    private final AsyncCache<MidTermKey, KmaMidTermItem> midTerm;
    private final AsyncCache<SunTimesKey, SunTimes> sunTimes;

    public WeatherCaches(MeterRegistry meterRegistry) {
        this.shortTerm = build(SHORT_TERM_TTL, SHORT_TERM_MAX);
        this.midTerm = build(MID_TERM_TTL, MID_TERM_MAX);
        this.sunTimes = build(SUN_TIMES_TTL, SUN_TIMES_MAX);
        CaffeineCacheMetrics.monitor(meterRegistry, shortTerm, "kmaShortTerm");
        CaffeineCacheMetrics.monitor(meterRegistry, midTerm, "kmaMidTerm");
        CaffeineCacheMetrics.monitor(meterRegistry, sunTimes, "kasiSunTimes");
    }

    List<ShortTermForecast> shortTerm(
            LocalDateTime baseDateTime, int nx, int ny, Supplier<List<ShortTermForecast>> loader) {
        return load(shortTerm, new ShortTermKey(baseDateTime, nx, ny), loader);
    }

    Optional<KmaMidTermItem> midTerm(
            String operation, String regId, LocalDateTime tmFc, Supplier<Optional<KmaMidTermItem>> loader) {
        return Optional.ofNullable(load(
                midTerm,
                new MidTermKey(operation, regId, tmFc),
                () -> loader.get().orElse(null)));
    }

    Optional<SunTimes> sunTimes(double lat, double lng, LocalDate date, Supplier<Optional<SunTimes>> loader) {
        return Optional.ofNullable(load(
                sunTimes, new SunTimesKey(date, lat, lng), () -> loader.get().orElse(null)));
    }

    public void clear() {
        shortTerm.synchronous().invalidateAll();
        midTerm.synchronous().invalidateAll();
        sunTimes.synchronous().invalidateAll();
    }

    private static <K, V> V load(AsyncCache<K, V> cache, K key, Supplier<V> loader) {
        // getIfPresent는 캐시 지표에 적중·누락을 남기고, asMap()은 남기지 않는다.
        CompletableFuture<V> existing = cache.getIfPresent(key);
        if (existing == null) {
            CompletableFuture<V> mine = new CompletableFuture<>();
            existing = cache.asMap().putIfAbsent(key, mine);
            if (existing == null) {
                return loadInto(mine, loader);
            }
        }
        return join(existing);
    }

    private static <V> V loadInto(CompletableFuture<V> future, Supplier<V> loader) {
        V value;
        try {
            value = loader.get();
        } catch (Throwable e) {
            future.completeExceptionally(e);
            throw e;
        }
        future.complete(value);
        return value;
    }

    private static <V> V join(CompletableFuture<V> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            if (e.getCause() instanceof Error cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static <K, V> AsyncCache<K, V> build(Duration ttl, long maximumSize) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maximumSize)
                .recordStats()
                .buildAsync();
    }

    private record ShortTermKey(LocalDateTime baseDateTime, int nx, int ny) {}

    private record MidTermKey(String operation, String regId, LocalDateTime tmFc) {}

    private record SunTimesKey(LocalDate date, double lat, double lng) {}
}
