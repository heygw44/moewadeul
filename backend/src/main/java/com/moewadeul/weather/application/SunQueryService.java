package com.moewadeul.weather.application;

import com.moewadeul.common.external.ExternalApiMetrics;
import com.moewadeul.common.transaction.OutsideTransaction;
import com.moewadeul.weather.infra.KasiApiException;
import com.moewadeul.weather.infra.KasiClient;
import com.moewadeul.weather.infra.KasiRiseSetItem;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SunQueryService {

    private static final String API = "kasi";

    // 경도 0.05도는 출몰시각을 12초 옮기고, 천문연은 분 단위로 준다. 그래서 0.1도 칸의 가운데 좌표로 묻고 그 칸의 장소끼리 결과를 나눠 쓴다.
    private static final double CELLS_PER_DEGREE = 10;

    private final KasiClient kasiClient;
    private final WeatherCaches caches;
    private final ExternalApiMetrics metrics;

    public Optional<SunTimes> sunTimes(double lat, double lng, LocalDate date) {
        OutsideTransaction.require("천문연 응답을 기다리는 동안 DB 커넥션을 쥐면 안 된다.");
        try {
            double cellLat = toCell(lat);
            double cellLng = toCell(lng);
            return caches.sunTimes(cellLat, cellLng, date, () -> fetch(cellLat, cellLng, date));
        } catch (WeatherCacheBusyException e) {
            log.warn("천문연 응답을 기다리는 자리가 없어 출몰시각 없이 응답한다. reason={}", e.getMessage());
            metrics.recordCacheBusy(API);
            return Optional.empty();
        }
    }

    private static double toCell(double coordinate) {
        return Math.round(coordinate * CELLS_PER_DEGREE) / CELLS_PER_DEGREE;
    }

    private Optional<SunTimes> fetch(double lat, double lng, LocalDate date) {
        try {
            Optional<KasiRiseSetItem> item = kasiClient.getRiseSet(date, lat, lng);
            if (item.isEmpty()) {
                return fallback("천문연이 출몰시각 항목을 주지 않았다");
            }
            return Optional.of(KasiSunTimesConverter.toSunTimes(item.get()));
        } catch (KasiApiException e) {
            return fallback(e.getMessage());
        }
    }

    private Optional<SunTimes> fallback(String reason) {
        log.warn("천문연 출몰시각을 받지 못해 출몰시각 없이 응답한다. reason={}", reason);
        metrics.recordFallback(API);
        return Optional.empty();
    }
}
