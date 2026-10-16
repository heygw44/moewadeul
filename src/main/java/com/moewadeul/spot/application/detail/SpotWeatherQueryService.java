package com.moewadeul.spot.application.detail;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.common.time.KoreaTime;
import com.moewadeul.spot.infra.detail.SpotDetailMapper;
import com.moewadeul.spot.infra.detail.SpotWeatherLocationRow;
import com.moewadeul.weather.application.SunQueryService;
import com.moewadeul.weather.application.SunTimes;
import com.moewadeul.weather.application.WeatherForecast;
import com.moewadeul.weather.application.WeatherQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 장소 한 곳의 날씨와 오늘 출몰시각을 찾는다.
 *
 * <p>외부 API(기상청, 천문연)를 DB 트랜잭션 밖에서 부르려고 이 서비스에는 트랜잭션을 두지 않는다. 장소 좌표는 SELECT 한 문장으로 읽고 바로
 * 커넥션을 돌려준 뒤 외부 API를 부른다.
 */
@Service
@RequiredArgsConstructor
public class SpotWeatherQueryService {

    private final SpotDetailMapper spotDetailMapper;
    private final WeatherQueryService weatherQueryService;
    private final SunQueryService sunQueryService;
    private final Clock clock;

    /**
     * 호출하면 spotId인 장소의 날씨를 돌려준다.
     *
     * <p>장소가 없거나 ACTIVE가 아니면 서비스는 외부 API를 부르지 않고 NOT_FOUND로 거부한다. 기상청 호출이 실패하면 빈 값을 돌려주고, 이때
     * 천문연은 부르지 않는다. 기상청이 성공하고 천문연만 실패하면 sun만 null로 둔다. 출몰시각의 날짜는 오늘 한국 날짜다.
     */
    public Optional<SpotWeather> findWeather(long spotId) {
        SpotWeatherLocationRow location = spotDetailMapper
                .selectActiveWeatherLocation(spotId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Optional<WeatherForecast> forecast = weatherQueryService.forecast(
                location.weatherNx(), location.weatherNy(), location.lat(), location.lng());
        if (forecast.isEmpty()) {
            return Optional.empty();
        }
        LocalDate today = LocalDate.now(clock.withZone(KoreaTime.ZONE));
        SunTimes sun =
                sunQueryService.sunTimes(location.lat(), location.lng(), today).orElse(null);
        return Optional.of(new SpotWeather(forecast.get(), sun));
    }
}
