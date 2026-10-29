package com.moewadeul.spot.infra.publicspot;

import com.moewadeul.spot.application.PublicSpotOperatingStatus;
import java.time.LocalDate;

public record PublicSpotRow(
        long spotId,
        String externalId,
        String name,
        String address,
        double latitude,
        double longitude,
        String category,
        String facilities,
        String phone,
        String homepage,
        PublicSpotOperatingStatus operatingStatus,
        LocalDate closedFrom,
        LocalDate closedUntil,
        LocalDate sourceDate,
        boolean sourceRemoved) {}
