package com.moewadeul.trip.infra;

import java.time.LocalDate;

public record SoloTripRow(
        long soloTripId, long spotId, String spotName, String spotType, LocalDate startDate, LocalDate endDate) {}
