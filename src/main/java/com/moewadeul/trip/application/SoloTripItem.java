package com.moewadeul.trip.application;

import com.moewadeul.trip.infra.SoloTripRow;
import java.time.LocalDate;

/** 내 개인 일정 한 건. spotType은 장소 유형 이름(CAMPSITE, FOREST, BAKJI)이다. */
public record SoloTripItem(
        long soloTripId, long spotId, String spotName, String spotType, LocalDate startDate, LocalDate endDate) {

    static SoloTripItem from(SoloTripRow row) {
        return new SoloTripItem(
                row.soloTripId(), row.spotId(), row.spotName(), row.spotType(), row.startDate(), row.endDate());
    }
}
