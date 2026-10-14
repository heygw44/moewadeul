package com.moewadeul.basecamp.api.membership;

import com.moewadeul.basecamp.application.membership.BasecampRejectResult;

public record BasecampRejectResponse(long applicationId, String status) {

    static BasecampRejectResponse from(BasecampRejectResult result) {
        return new BasecampRejectResponse(result.applicationId(), result.status());
    }
}
