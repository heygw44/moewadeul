package com.moewadeul.spot.infra.detail;

import com.moewadeul.spot.domain.SpotType;
import java.time.Instant;
import java.time.LocalDate;

public record SpotDetailRow(
        long spotId,
        SpotType type,
        String name,
        double lat,
        double lng,
        String address,
        boolean parkWarning,
        String areaName,
        String areaSource,
        LocalDate areaSourceDate,
        String description,
        Boolean hasWater,
        Boolean hasToilet,
        String signalLevel,
        long confirmationCount,
        Long reporterId,
        String reporterNickname,
        String source,
        String category,
        String facilities,
        String phone,
        String homepage,
        LocalDate sourceDate,
        String operatingStatus,
        LocalDate closedFrom,
        LocalDate closedUntil,
        Instant checkedAt) {}
