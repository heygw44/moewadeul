package com.moewadeul.basecamp.api.membership;

import com.moewadeul.basecamp.application.membership.BasecampApplyResult;
import com.moewadeul.basecamp.domain.BasecampApplicationStatus;

public record BasecampApplyResponse(long applicationId, BasecampApplicationStatus status) {

    static BasecampApplyResponse from(BasecampApplyResult result) {
        return new BasecampApplyResponse(result.applicationId(), result.status());
    }
}
