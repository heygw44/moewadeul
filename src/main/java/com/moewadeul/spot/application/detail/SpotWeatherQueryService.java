package com.moewadeul.spot.application.detail;

import com.moewadeul.common.error.BusinessException;
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

@Service
@RequiredArgsConstructor
public class SpotWeatherQueryService {

    private final SpotDetailMapper spotDetailMapper;
    private final WeatherQueryService weatherQueryService;
    private final SunQueryService sunQueryService;
    private final Clock clock;

    public Optional<SpotWeather> findWeather(long spotId) {
        SpotWeatherLocationRow location =
                spotDetailMapper.selectActiveWeatherLocation(spotId).orElseThrow(BusinessException::notFound);
        Optional<WeatherForecast> forecast = weatherQueryService.forecast(
                location.weatherNx(), location.weatherNy(), location.lat(), location.lng());
        if (forecast.isEmpty()) {
            return Optional.empty();
        }
        LocalDate today = KoreaTime.today(clock);
        SunTimes sun =
                sunQueryService.sunTimes(location.lat(), location.lng(), today).orElse(null);
        return Optional.of(new SpotWeather(forecast.get(), sun));
    }
}
