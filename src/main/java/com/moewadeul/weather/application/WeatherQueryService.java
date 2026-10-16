package com.moewadeul.weather.application;

import com.moewadeul.common.external.ExternalApiMetrics;
import com.moewadeul.common.time.KoreaTime;
import com.moewadeul.weather.application.WeatherForecast.MidTermForecast;
import com.moewadeul.weather.application.WeatherForecast.ShortTermForecast;
import com.moewadeul.weather.domain.KmaBaseTimes;
import com.moewadeul.weather.domain.MidTermRegion;
import com.moewadeul.weather.domain.MidTermRegions;
import com.moewadeul.weather.infra.KmaApiException;
import com.moewadeul.weather.infra.KmaClient;
import com.moewadeul.weather.infra.KmaMidTermItem;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 장소의 날씨 예보를 기상청에서 조회한다.
 *
 * <p>외부 API 호출을 DB 트랜잭션 안에서 하지 않으려고 이 서비스에는 트랜잭션이 없다. 기상청이 실패하거나 응답을 해석하지 못하면 예외를 던지지
 * 않고 빈 값을 돌려준다. 그래서 장소 상세는 날씨 없이도 정상으로 응답한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherQueryService {

    private static final String API = "kma";

    private static final String MID_LAND_FCST = "getMidLandFcst";
    private static final String MID_TA = "getMidTa";

    private final KmaClient kmaClient;
    private final WeatherCaches caches;
    private final MidTermRegions midTermRegions;
    private final ExternalApiMetrics metrics;
    private final Clock clock;

    /**
     * 장소의 단기·중기 예보를 조회한다. 단기, 중기 육상, 중기 기온 순서로 부르고, 하나라도 실패하면 남은 호출을 하지 않고 빈 값을 돌려준다.
     * 일부 값만 있는 날씨를 보여 주면 사용자가 빠진 값을 "비 안 옴" 같은 뜻으로 읽을 수 있기 때문이다.
     *
     * <p>같은 격자·같은 발표 시각의 예보는 {@link WeatherCaches}에 둔 값을 쓰고 기상청을 다시 부르지 않는다.
     *
     * @param nx 장소의 기상청 격자 X
     * @param ny 장소의 기상청 격자 Y
     * @param lat 장소의 위도. 중기예보 구역을 고를 때 쓴다.
     * @param lng 장소의 경도
     * @return 예보. 기상청 호출이 실패했거나 응답을 해석하지 못했으면 빈 값
     */
    public Optional<WeatherForecast> forecast(int nx, int ny, double lat, double lng) {
        try {
            return Optional.of(fetchForecast(nx, ny, lat, lng));
        } catch (KmaApiException e) {
            log.warn("기상청 날씨를 받지 못해 날씨 없이 응답한다. reason={}", e.getMessage());
            metrics.recordFallback(API);
            return Optional.empty();
        }
    }

    private WeatherForecast fetchForecast(int nx, int ny, double lat, double lng) {
        Instant now = clock.instant();
        LocalDateTime base = KmaBaseTimes.shortTermBase(now);
        // 서버는 발표 하나의 예보를 통째로 캐시에 두고, 요청할 때마다 지금 이후 시각만 골라 준다.
        List<ShortTermForecast> published = caches.shortTerm(
                base, nx, ny, () -> KmaForecastConverter.toShortTerm(kmaClient.getVilageFcst(base, nx, ny)));
        List<ShortTermForecast> shortTerm = KmaForecastConverter.upcoming(published, now);

        MidTermRegion region = midTermRegions.nearest(lat, lng);
        LocalDateTime tmFc = KmaBaseTimes.midTermBase(now);
        Optional<KmaMidTermItem> land = caches.midTerm(
                MID_LAND_FCST, region.landRegId(), tmFc, () -> kmaClient.getMidLandFcst(region.landRegId(), tmFc));
        Optional<KmaMidTermItem> ta =
                caches.midTerm(MID_TA, region.taRegId(), tmFc, () -> kmaClient.getMidTa(region.taRegId(), tmFc));
        List<MidTermForecast> midTerm =
                KmaForecastConverter.toMidTerm(land, ta, tmFc.toLocalDate(), LocalDate.ofInstant(now, KoreaTime.ZONE));

        return new WeatherForecast(WeatherForecast.KMA_SOURCE, shortTerm, midTerm);
    }
}
