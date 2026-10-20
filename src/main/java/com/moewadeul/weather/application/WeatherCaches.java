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

/**
 * 기상청·천문연에서 받은 결과를 서버 메모리에 잠시 둔다.
 *
 * <p>같은 격자·같은 발표 시각의 예보와 같은 위치·같은 날짜의 출몰시각은 다시 받아도 값이 같다. 그런데 기상청 단기예보 한 건은 1초 넘게 걸리고, 장소를
 * 열 때마다 부르면 하루 호출 한도를 이용자 수에 비례해 쓴다. 그래서 서버는 성공한 결과만 여기에 넣고, 실패한 호출은 넣지 않아 다음 요청이 다시 부르게
 * 한다.
 *
 * <p>키에 발표 시각이나 날짜가 들어 있어서 새 발표가 나오면 서버는 새 키로 다시 부른다. 만료 시간은 지난 발표를 메모리에서 비우려고 둔다.
 *
 * <p>키가 비는 순간(새 발표, 새 날짜)에는 같은 키로 요청이 한꺼번에 들어온다. 그래서 서버는 키마다 외부 API를 한 번만 부른다. 처음 읽은
 * 요청이 아직 끝나지 않은 결과를 먼저 캐시에 넣고 자기 스레드에서 외부 API를 부르며, 같은 키로 뒤따라온 요청은 그 결과를 기다린다. 첫 호출이
 * 실패하면 기다리던 요청도 같은 실패를 받고, 서버는 그 결과를 캐시에서 지워 다음 요청이 다시 부르게 한다. Caffeine의 {@code get(key,
 * loader)}는 계산하는 동안 다른 키의 갱신까지 막을 수 있어서 쓰지 않는다. 이 방식은 다른 키를 막지 않는다. 캐시 지표의 적중 수에는 첫 호출을
 * 기다린 요청도 들어간다.
 */
@Component
public class WeatherCaches {

    // 단기예보는 3시간마다, 중기예보는 12시간마다 발표된다. 한 발표의 값은 다음 발표 전까지만 쓰인다.
    private static final Duration SHORT_TERM_TTL = Duration.ofHours(3);
    private static final Duration MID_TERM_TTL = Duration.ofHours(12);
    private static final Duration SUN_TIMES_TTL = Duration.ofHours(24);
    // 2026-10-16 로컬 DB의 ACTIVE 장소 3,117곳이 기상청 격자 1,594개를 썼다. 값 하나는 시간별 예보 70~80개다.
    private static final long SHORT_TERM_MAX = 2_000;
    // 중기예보 구역은 수십 곳이고, 오퍼레이션 두 개가 각각 둔다.
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

    /**
     * 발표 하나의 시간별 예보 전체를 돌려준다. 없으면 loader로 받아 넣는다. loader가 예외를 던지면 넣지 않고 그대로 던지며, 같은 키의 첫 호출을
     * 기다리던 요청도 같은 예외를 받는다.
     */
    List<ShortTermForecast> shortTerm(
            LocalDateTime baseDateTime, int nx, int ny, Supplier<List<ShortTermForecast>> loader) {
        return load(shortTerm, new ShortTermKey(baseDateTime, nx, ny), loader);
    }

    /**
     * 중기예보 원천 항목을 돌려준다. 없으면 loader로 받아 넣는다. 원천이 항목을 주지 않은 빈 값은 잠깐의 누락일 수 있어서 넣지 않는다.
     *
     * @param operation 기상청 오퍼레이션 이름. 육상예보와 기온예보는 같은 구역 코드를 쓸 수 있어서 키에 넣는다.
     */
    Optional<KmaMidTermItem> midTerm(
            String operation, String regId, LocalDateTime tmFc, Supplier<Optional<KmaMidTermItem>> loader) {
        return Optional.ofNullable(load(
                midTerm,
                new MidTermKey(operation, regId, tmFc),
                () -> loader.get().orElse(null)));
    }

    /** 위치·날짜의 출몰시각을 돌려준다. 없으면 loader로 받아 넣는다. 빈 값은 넣지 않는다. */
    Optional<SunTimes> sunTimes(double lat, double lng, LocalDate date, Supplier<Optional<SunTimes>> loader) {
        return Optional.ofNullable(load(
                sunTimes, new SunTimesKey(date, lat, lng), () -> loader.get().orElse(null)));
    }

    /** 모든 캐시를 비운다. 테스트가 서로의 결과를 보지 않게 하려고 쓴다. */
    public void clear() {
        shortTerm.synchronous().invalidateAll();
        midTerm.synchronous().invalidateAll();
        sunTimes.synchronous().invalidateAll();
    }

    /**
     * 캐시에 값이나 진행 중인 첫 호출이 있으면 그 결과를 기다려 돌려준다. 없으면 이 요청이 첫 호출을 맡아 자기 스레드에서 loader를 실행한다.
     *
     * <p>loader가 {@code null}을 돌려주거나 예외를 던지면 Caffeine이 그 결과를 캐시에서 지운다. 그래서 빈 값과 실패는 남지 않는다.
     */
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

    // Error까지 결과에 넘겨, 첫 호출이 어떻게 끝나도 기다리던 요청이 멈춘 채 남지 않게 한다.
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

    // 첫 호출이 던진 예외를 감싸지 않고 그대로 던진다. 호출하는 쪽은 외부 API 예외(KmaApiException 등)를 잡아 날씨 없이 응답한다.
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
