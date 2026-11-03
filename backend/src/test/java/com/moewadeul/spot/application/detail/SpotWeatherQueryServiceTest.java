package com.moewadeul.spot.application.detail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import com.moewadeul.spot.infra.detail.SpotDetailMapper;
import com.moewadeul.spot.infra.detail.SpotWeatherLocationRow;
import com.moewadeul.weather.application.LocationWeather;
import com.moewadeul.weather.application.LocationWeatherService;
import com.moewadeul.weather.application.WeatherForecast;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpotWeatherQueryServiceTest {

    private static final long SPOT_ID = 7L;

    private final SpotDetailMapper mapper = mock(SpotDetailMapper.class);
    private final LocationWeatherService locationWeatherService = mock(LocationWeatherService.class);
    private final SpotWeatherQueryService service = new SpotWeatherQueryService(mapper, locationWeatherService);

    @Test
    @DisplayName("[F-09] 장소의 격자와 좌표를 날씨 서비스에 그대로 넘기고 그 결과를 돌려준다")
    void delegatesLocationToWeatherService() {
        // given
        when(mapper.selectActiveWeatherLocation(SPOT_ID))
                .thenReturn(Optional.of(new SpotWeatherLocationRow(37.5, 127.0, 60, 127)));
        CompletableFuture<Optional<LocationWeather>> expected = CompletableFuture.completedFuture(
                Optional.of(new LocationWeather(new WeatherForecast("기상청", List.of(), List.of()), null)));
        when(locationWeatherService.findWeather(60, 127, 37.5, 127.0)).thenReturn(expected);

        // when
        CompletableFuture<Optional<LocationWeather>> actual = service.findWeather(SPOT_ID);

        // then
        assertThat(actual).isSameAs(expected);
        verify(locationWeatherService).findWeather(60, 127, 37.5, 127.0);
    }

    @Test
    @DisplayName("[F-09] 장소가 없으면 날씨 서비스를 부르지 않고 바로 404 예외를 던진다")
    void throwsNotFoundBeforeCallingWeatherService() {
        // given
        when(mapper.selectActiveWeatherLocation(SPOT_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.findWeather(SPOT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        verifyNoInteractions(locationWeatherService);
    }
}
