package com.moewadeul.spot.application.publicspot;

import com.moewadeul.spot.application.PublicSpotOperatingStatus;
import com.moewadeul.spot.application.PublicSpotSetting;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

public record PublicSpotCommand(
        String externalId,
        String name,
        double latitude,
        double longitude,
        String address,
        String category,
        Map<String, String> facilities,
        String phone,
        String homepage,
        PublicSpotOperatingStatus operatingStatus,
        LocalDate closedFrom,
        LocalDate closedUntil,
        LocalDate sourceDate,
        Set<PublicSpotSetting> settings) {

    public PublicSpotCommand {
        settings = settings == null ? Set.of() : Set.copyOf(settings);
    }
}
