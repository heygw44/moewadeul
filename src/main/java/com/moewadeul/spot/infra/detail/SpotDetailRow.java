package com.moewadeul.spot.infra.detail;

import com.moewadeul.spot.application.PublicSpotOperatingStatus;
import com.moewadeul.spot.application.park.ProtectedAreaSource;
import com.moewadeul.spot.application.publicspot.PublicSpotSource;
import com.moewadeul.spot.domain.SpotType;
import com.moewadeul.spot.domain.bakji.BakjiSignalLevel;
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
        ProtectedAreaSource areaSource,
        LocalDate areaSourceDate,
        String description,
        Boolean hasWater,
        Boolean hasToilet,
        BakjiSignalLevel signalLevel,
        long confirmationCount,
        Long reporterId,
        String reporterNickname,
        PublicSpotSource source,
        String category,
        String facilities,
        String phone,
        String homepage,
        LocalDate sourceDate,
        PublicSpotOperatingStatus operatingStatus,
        LocalDate closedFrom,
        LocalDate closedUntil,
        Instant checkedAt) {}
