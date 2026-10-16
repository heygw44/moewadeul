package com.moewadeul.spot.application.detail;

import com.moewadeul.weather.application.SunTimes;
import com.moewadeul.weather.application.WeatherForecast;

/**
 * 장소의 날씨. forecast는 기상청 예보이고, sun은 오늘(한국 날짜) 출몰시각이다.
 *
 * <p>천문연이 실패했으면 sun만 {@code null}이다. 기상청이 실패했으면 서비스는 이 객체를 만들지 않고 빈 값을 돌려준다.
 */
public record SpotWeather(WeatherForecast forecast, SunTimes sun) {}
