package com.moewadeul.trip.application;

import com.moewadeul.trip.infra.SoloTripRow;
import java.time.LocalDate;

public record SoloTripItem(
        long soloTripId, long spotId, String spotName, String spotType, LocalDate startDate, LocalDate endDate) {

    static SoloTripItem from(SoloTripRow row) {
        return new SoloTripItem(
                row.soloTripId(), row.spotId(), row.spotName(), row.spotType(), row.startDate(), row.endDate());
    }
}
