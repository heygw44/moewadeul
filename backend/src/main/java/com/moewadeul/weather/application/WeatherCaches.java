package com.moewadeul.weather.application;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.ConcurrentStatsCounter;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;
import com.moewadeul.weather.application.WeatherForecast.ShortTermForecast;
import com.moewadeul.weather.infra.KmaMidTermItem;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class WeatherCaches {

    private static final String WAITS_COUNTER_NAME = "moewadeul.weather.cache.waits";
    private static final Duration SHORT_TERM_TTL = Duration.ofHours(3);
    private static final Duration MID_TERM_TTL = Duration.ofHours(12);
    private static final Duration SUN_TIMES_TTL = Duration.ofHours(24);
    private static final long SHORT_TERM_MAX = 2_000;
    private static final long MID_TERM_MAX = 500;
    private static final long SUN_TIMES_MAX = 10_000;
    // 다른 요청이 받는 중인 값을 기다리는 스레드는 외부 호출 한도를 거치지 않으므로, 세 캐시를 합쳐 따로 막는다.
    static final int MAX_WAITERS = 10;

    private final Tier<ShortTermKey, List<ShortTermForecast>> shortTerm;
    private final Tier<MidTermKey, KmaMidTermItem> midTerm;
    private final Tier<SunTimesKey, SunTimes> sunTimes;
    private final Semaphore waiters = new Semaphore(MAX_WAITERS);

    public WeatherCaches(MeterRegistry meterRegistry) {
        this.shortTerm = Tier.build(meterRegistry, "kmaShortTerm", SHORT_TERM_TTL, SHORT_TERM_MAX);
        this.midTerm = Tier.build(meterRegistry, "kmaMidTerm", MID_TERM_TTL, MID_TERM_MAX);
        this.sunTimes = Tier.build(meterRegistry, "kasiSunTimes", SUN_TIMES_TTL, SUN_TIMES_MAX);
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
        shortTerm.cache().synchronous().invalidateAll();
        midTerm.cache().synchronous().invalidateAll();
        sunTimes.cache().synchronous().invalidateAll();
    }

    // Caffeine의 getIfPresent는 아직 받는 중인 값도 적중으로 센다. 그래서 asMap()으로 찾고 적중·누락·대기를 직접 센다.
    private <K, V> V load(Tier<K, V> tier, K key, Supplier<V> loader) {
        CompletableFuture<V> existing = tier.cache().asMap().get(key);
        if (existing == null) {
            CompletableFuture<V> mine = new CompletableFuture<>();
            existing = tier.cache().asMap().putIfAbsent(key, mine);
            if (existing == null) {
                tier.stats().recordMisses(1);
                return loadInto(mine, loader);
            }
        }
        if (existing.isDone()) {
            tier.stats().recordHits(1);
            return join(existing);
        }
        return await(tier, existing);
    }

    private <V> V await(Tier<?, V> tier, CompletableFuture<V> future) {
        if (!waiters.tryAcquire()) {
            throw new WeatherCacheBusyException();
        }
        tier.waits().increment();
        try {
            return join(future);
        } finally {
            waiters.release();
        }
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

    private record Tier<K, V>(AsyncCache<K, V> cache, StatsCounter stats, Counter waits) {

        static <K, V> Tier<K, V> build(MeterRegistry registry, String name, Duration ttl, long maximumSize) {
            StatsCounter stats = new ConcurrentStatsCounter();
            AsyncCache<K, V> cache = Caffeine.newBuilder()
                    .expireAfterWrite(ttl)
                    .maximumSize(maximumSize)
                    .recordStats(() -> stats)
                    .buildAsync();
            CaffeineCacheMetrics.monitor(registry, cache, name);
            Counter waits = Counter.builder(WAITS_COUNTER_NAME)
                    .description("받는 중인 첫 호출이 끝나기를 기다린 요청 수. cache_gets의 hit와 miss 어디에도 들지 않는다")
                    .tag("cache", name)
                    .register(registry);
            return new Tier<>(cache, stats, waits);
        }
    }

    private record ShortTermKey(LocalDateTime baseDateTime, int nx, int ny) {}

    private record MidTermKey(String operation, String regId, LocalDateTime tmFc) {}

    private record SunTimesKey(LocalDate date, double lat, double lng) {}
}
