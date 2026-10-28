package com.moewadeul.spot.infra.publicspot;

import java.time.LocalDate;

public record PublicSpotColumns(
        String externalId,
        String name,
        double latitude,
        double longitude,
        String address,
        int weatherNx,
        int weatherNy,
        String category,
        String facilities,
        String phone,
        String homepage,
        String operatingStatus,
        LocalDate closedFrom,
        LocalDate closedUntil,
        LocalDate sourceDate) {}
