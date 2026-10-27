package com.moewadeul.spot.application.detail;

import com.moewadeul.weather.application.SunTimes;
import com.moewadeul.weather.application.WeatherForecast;

public record SpotWeather(WeatherForecast forecast, SunTimes sun) {}
