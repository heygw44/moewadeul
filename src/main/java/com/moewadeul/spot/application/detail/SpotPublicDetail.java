package com.moewadeul.spot.application.detail;

import com.moewadeul.spot.application.PublicSpotOperatingStatus;
import com.moewadeul.spot.application.publicspot.PublicSpotSource;
import java.time.Instant;
import java.time.LocalDate;

public record SpotPublicDetail(
        PublicSpotSource source,
        String category,
        SpotFacilities facilities,
        String phone,
        String homepage,
        LocalDate sourceDate,
        Instant checkedAt,
        PublicSpotOperatingStatus operatingStatus,
        LocalDate closedFrom,
        LocalDate closedUntil,
        boolean closedNow) {}
