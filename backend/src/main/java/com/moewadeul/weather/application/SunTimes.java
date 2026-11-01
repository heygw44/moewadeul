package com.moewadeul.weather.application;

import java.time.LocalDate;
import java.time.LocalTime;

public record SunTimes(LocalDate date, LocalTime sunrise, LocalTime sunset, LocalTime civilTwilightEnd) {}
