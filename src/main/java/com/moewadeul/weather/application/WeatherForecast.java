package com.moewadeul.weather.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record WeatherForecast(String source, List<ShortTermForecast> shortTerm, List<MidTermForecast> midTerm) {

    public static final String KMA_SOURCE = "기상청";

    public WeatherForecast {
        shortTerm = List.copyOf(shortTerm);
        midTerm = List.copyOf(midTerm);
    }

    public record ShortTermForecast(
            Instant at, Integer temperature, Integer precipitationProbability, Double windSpeed) {}

    public record MidTermForecast(
            LocalDate date,
            Integer minTemperature,
            Integer maxTemperature,
            String amSky,
            String pmSky,
            Integer amPrecipitationProbability,
            Integer pmPrecipitationProbability) {}
}
