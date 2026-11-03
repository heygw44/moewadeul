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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherQueryService {

    private static final String MID_LAND_FCST = "getMidLandFcst";
    private static final String MID_TA = "getMidTa";

    private final KmaClient kmaClient;
    private final WeatherCaches caches;
    private final MidTermRegions midTermRegions;
    private final ExternalApiMetrics metrics;
    private final Clock clock;

    public Optional<WeatherForecast> forecast(int nx, int ny, double lat, double lng) {
        try {
            return Optional.of(fetchForecast(nx, ny, lat, lng));
        } catch (KmaApiException e) {
            log.warn("기상청 날씨를 받지 못해 날씨 없이 응답한다. reason={}", e.getMessage());
            metrics.recordFallback(KmaClient.API);
            return Optional.empty();
        }
    }

    public boolean isForecastReady(int nx, int ny, double lat, double lng) {
        ForecastKeys keys = keysAt(clock.instant(), lat, lng);
        return caches.shortTermReady(keys.base(), nx, ny)
                && caches.midTermReady(MID_LAND_FCST, keys.region().landRegId(), keys.tmFc())
                && caches.midTermReady(MID_TA, keys.region().taRegId(), keys.tmFc());
    }

    private WeatherForecast fetchForecast(int nx, int ny, double lat, double lng) {
        Instant now = clock.instant();
        ForecastKeys keys = keysAt(now, lat, lng);
        LocalDateTime base = keys.base();
        MidTermRegion region = keys.region();
        LocalDateTime tmFc = keys.tmFc();
        List<ShortTermForecast> published = caches.shortTerm(
                base, nx, ny, () -> KmaForecastConverter.toShortTerm(kmaClient.getVilageFcst(base, nx, ny)));
        List<ShortTermForecast> shortTerm = KmaForecastConverter.upcoming(published, now);

        Optional<KmaMidTermItem> land = caches.midTerm(
                MID_LAND_FCST, region.landRegId(), tmFc, () -> kmaClient.getMidLandFcst(region.landRegId(), tmFc));
        Optional<KmaMidTermItem> ta =
                caches.midTerm(MID_TA, region.taRegId(), tmFc, () -> kmaClient.getMidTa(region.taRegId(), tmFc));
        List<MidTermForecast> midTerm =
                KmaForecastConverter.toMidTerm(land, ta, tmFc.toLocalDate(), KoreaTime.dateOf(now));

        return new WeatherForecast(WeatherForecast.KMA_SOURCE, shortTerm, midTerm);
    }

    private ForecastKeys keysAt(Instant now, double lat, double lng) {
        return new ForecastKeys(
                KmaBaseTimes.shortTermBase(now), midTermRegions.nearest(lat, lng), KmaBaseTimes.midTermBase(now));
    }

    private record ForecastKeys(LocalDateTime base, MidTermRegion region, LocalDateTime tmFc) {}
}
