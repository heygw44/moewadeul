package com.pitchmap.trip.api;

import com.pitchmap.trip.application.SoloTripItem;
import java.time.LocalDate;

public record SoloTripItemResponse(long soloTripId, Spot spot, LocalDate startDate, LocalDate endDate) {

    public record Spot(long spotId, String name, String type) {}

    static SoloTripItemResponse from(SoloTripItem item) {
        return new SoloTripItemResponse(
                item.soloTripId(),
                new Spot(item.spotId(), item.spotName(), item.spotType()),
                item.startDate(),
                item.endDate());
    }
}
