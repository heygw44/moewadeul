package com.pitchmap.trip.application;

import java.time.LocalDate;

public record SoloTripRegisterCommand(long spotId, LocalDate startDate, LocalDate endDate) {}
