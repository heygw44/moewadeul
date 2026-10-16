package com.moewadeul.spot.api.detail;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.moewadeul.spot.application.detail.SpotWeather;
import com.moewadeul.weather.application.SunTimes;
import com.moewadeul.weather.application.WeatherForecast;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// 장소 날씨 응답. 기상청 호출이 실패하면 weather가 null이고, 천문연만 실패하면 weather 안의 sun이 null이다. 두 경우 모두 필드는 응답에 남는다.
public record SpotWeatherResponse(Weather weather) {

    static SpotWeatherResponse from(SpotWeather weather) {
        return new SpotWeatherResponse(weather == null ? null : Weather.from(weather));
    }

    /** sun은 천문연이 실패해도 필드를 남기고 null로 준다. */
    public record Weather(String source, List<ShortTerm> shortTerm, List<MidTerm> midTerm, Sun sun) {

        static Weather from(SpotWeather weather) {
            WeatherForecast forecast = weather.forecast();
            return new Weather(
                    forecast.source(),
                    forecast.shortTerm().stream().map(ShortTerm::from).toList(),
                    forecast.midTerm().stream().map(MidTerm::from).toList(),
                    weather.sun() == null ? null : Sun.from(weather.sun()));
        }
    }

    public record ShortTerm(Instant at, Integer temperature, Integer precipitationProbability, Double windSpeed) {

        static ShortTerm from(WeatherForecast.ShortTermForecast forecast) {
            return new ShortTerm(
                    forecast.at(), forecast.temperature(), forecast.precipitationProbability(), forecast.windSpeed());
        }
    }

    public record MidTerm(
            LocalDate date,
            Integer minTemperature,
            Integer maxTemperature,
            String amSky,
            String pmSky,
            Integer amPrecipitationProbability,
            Integer pmPrecipitationProbability) {

        static MidTerm from(WeatherForecast.MidTermForecast forecast) {
            return new MidTerm(
                    forecast.date(),
                    forecast.minTemperature(),
                    forecast.maxTemperature(),
                    forecast.amSky(),
                    forecast.pmSky(),
                    forecast.amPrecipitationProbability(),
                    forecast.pmPrecipitationProbability());
        }
    }

    // 시각은 초 없이 "HH:mm"(한국 시각)으로 내보낸다.
    public record Sun(
            LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime sunrise,
            @JsonFormat(pattern = "HH:mm") LocalTime sunset,
            @JsonFormat(pattern = "HH:mm") LocalTime civilTwilightEnd) {

        static Sun from(SunTimes sun) {
            return new Sun(sun.date(), sun.sunrise(), sun.sunset(), sun.civilTwilightEnd());
        }
    }
}
