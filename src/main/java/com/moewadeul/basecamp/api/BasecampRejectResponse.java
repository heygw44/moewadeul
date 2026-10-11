package com.moewadeul.basecamp.api;

import com.moewadeul.basecamp.application.BasecampRejectResult;

public record BasecampRejectResponse(long applicationId, String status) {

    static BasecampRejectResponse from(BasecampRejectResult result) {
        return new BasecampRejectResponse(result.applicationId(), result.status());
    }
}
