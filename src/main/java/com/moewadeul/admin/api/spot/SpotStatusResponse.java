package com.moewadeul.admin.api.spot;

import com.moewadeul.common.moderation.ModerationResult;

public record SpotStatusResponse(long spotId, String status) {

    static SpotStatusResponse from(ModerationResult result) {
        return new SpotStatusResponse(result.targetId(), result.status());
    }
}
