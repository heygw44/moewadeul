package com.moewadeul.weather.application;

import com.github.benmanes.caffeine.cache.Cache;
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
 * <p>읽을 때 값이 없으면 잠금 없이 외부 API를 부르고 넣는다. Caffeine의 {@code get(key, loader)}는 계산하는 동안 다른 키의 갱신까지 막을 수
 * 있어서, 1초 넘게 걸리는 호출에는 쓰지 않는다. 그래서 같은 키를 동시에 처음 읽는 요청들은 각자 외부 API를 부를 수 있다.
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

    private final Cache<ShortTermKey, List<ShortTermForecast>> shortTerm;
    private final Cache<MidTermKey, KmaMidTermItem> midTerm;
    private final Cache<SunTimesKey, SunTimes> sunTimes;

    public WeatherCaches(MeterRegistry meterRegistry) {
        this.shortTerm = build(SHORT_TERM_TTL, SHORT_TERM_MAX);
        this.midTerm = build(MID_TERM_TTL, MID_TERM_MAX);
        this.sunTimes = build(SUN_TIMES_TTL, SUN_TIMES_MAX);
        CaffeineCacheMetrics.monitor(meterRegistry, shortTerm, "kmaShortTerm");
        CaffeineCacheMetrics.monitor(meterRegistry, midTerm, "kmaMidTerm");
        CaffeineCacheMetrics.monitor(meterRegistry, sunTimes, "kasiSunTimes");
    }

    /** 발표 하나의 시간별 예보 전체를 돌려준다. 없으면 loader로 받아 넣는다. loader가 예외를 던지면 넣지 않고 그대로 던진다. */
    List<ShortTermForecast> shortTerm(
            LocalDateTime baseDateTime, int nx, int ny, Supplier<List<ShortTermForecast>> loader) {
        ShortTermKey key = new ShortTermKey(baseDateTime, nx, ny);
        List<ShortTermForecast> cached = shortTerm.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        List<ShortTermForecast> loaded = loader.get();
        shortTerm.put(key, loaded);
        return loaded;
    }

    /**
     * 중기예보 원천 항목을 돌려준다. 없으면 loader로 받아 넣는다. 원천이 항목을 주지 않은 빈 값은 잠깐의 누락일 수 있어서 넣지 않는다.
     *
     * @param operation 기상청 오퍼레이션 이름. 육상예보와 기온예보는 같은 구역 코드를 쓸 수 있어서 키에 넣는다.
     */
    Optional<KmaMidTermItem> midTerm(
            String operation, String regId, LocalDateTime tmFc, Supplier<Optional<KmaMidTermItem>> loader) {
        MidTermKey key = new MidTermKey(operation, regId, tmFc);
        KmaMidTermItem cached = midTerm.getIfPresent(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<KmaMidTermItem> loaded = loader.get();
        loaded.ifPresent(item -> midTerm.put(key, item));
        return loaded;
    }

    /** 위치·날짜의 출몰시각을 돌려준다. 없으면 loader로 받아 넣는다. 빈 값은 넣지 않는다. */
    Optional<SunTimes> sunTimes(double lat, double lng, LocalDate date, Supplier<Optional<SunTimes>> loader) {
        SunTimesKey key = new SunTimesKey(date, lat, lng);
        SunTimes cached = sunTimes.getIfPresent(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<SunTimes> loaded = loader.get();
        loaded.ifPresent(value -> sunTimes.put(key, value));
        return loaded;
    }

    /** 모든 캐시를 비운다. 테스트가 서로의 결과를 보지 않게 하려고 쓴다. */
    public void clear() {
        shortTerm.invalidateAll();
        midTerm.invalidateAll();
        sunTimes.invalidateAll();
    }

    private static <K, V> Cache<K, V> build(Duration ttl, long maximumSize) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maximumSize)
                .recordStats()
                .build();
    }

    private record ShortTermKey(LocalDateTime baseDateTime, int nx, int ny) {}

    private record MidTermKey(String operation, String regId, LocalDateTime tmFc) {}

    private record SunTimesKey(LocalDate date, double lat, double lng) {}
}
