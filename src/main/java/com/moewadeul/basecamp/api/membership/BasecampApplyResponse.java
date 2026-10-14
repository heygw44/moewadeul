package com.moewadeul.basecamp.api.membership;

import com.moewadeul.basecamp.application.membership.BasecampApplyResult;

public record BasecampApplyResponse(long applicationId, String status) {

    static BasecampApplyResponse from(BasecampApplyResult result) {
        return new BasecampApplyResponse(result.applicationId(), result.status());
    }
}
