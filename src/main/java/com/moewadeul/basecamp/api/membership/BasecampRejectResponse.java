package com.moewadeul.basecamp.api.membership;

import com.moewadeul.basecamp.application.membership.BasecampRejectResult;
import com.moewadeul.basecamp.domain.BasecampApplicationStatus;

public record BasecampRejectResponse(long applicationId, BasecampApplicationStatus status) {

    static BasecampRejectResponse from(BasecampRejectResult result) {
        return new BasecampRejectResponse(result.applicationId(), result.status());
    }
}
