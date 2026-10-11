package com.pitchmap.trip.infra;

import java.time.LocalDate;

/** 내 개인 일정 목록의 한 행. 장소 이름과 유형은 조회할 때의 spot 행 값이다. */
public record SoloTripRow(
        long soloTripId, long spotId, String spotName, String spotType, LocalDate startDate, LocalDate endDate) {}
