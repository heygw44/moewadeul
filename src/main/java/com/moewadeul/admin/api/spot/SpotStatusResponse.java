package com.moewadeul.admin.api.spot;

import com.moewadeul.spot.application.moderation.SpotStatusResult;

public record SpotStatusResponse(long spotId, String status) {

    static SpotStatusResponse from(SpotStatusResult result) {
        return new SpotStatusResponse(result.spotId(), result.status());
    }
}
