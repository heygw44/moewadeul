package com.moewadeul.spot.application.detail;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.spot.infra.detail.SpotDetailMapper;
import com.moewadeul.spot.infra.detail.SpotWeatherLocationRow;
import com.moewadeul.weather.application.LocationWeather;
import com.moewadeul.weather.application.LocationWeatherService;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpotWeatherQueryService {

    private final SpotDetailMapper spotDetailMapper;
    private final LocationWeatherService locationWeatherService;

    public CompletableFuture<Optional<LocationWeather>> findWeather(long spotId) {
        SpotWeatherLocationRow location =
                spotDetailMapper.selectActiveWeatherLocation(spotId).orElseThrow(BusinessException::notFound);
        return locationWeatherService.findWeather(
                location.weatherNx(), location.weatherNy(), location.lat(), location.lng());
    }
}
