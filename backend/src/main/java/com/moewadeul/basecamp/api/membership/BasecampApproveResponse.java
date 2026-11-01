package com.moewadeul.basecamp.api.membership;

import com.moewadeul.basecamp.application.membership.BasecampApproveResult;
import com.moewadeul.basecamp.domain.BasecampStatus;

public record BasecampApproveResponse(int headcount, BasecampStatus status) {

    static BasecampApproveResponse from(BasecampApproveResult result) {
        return new BasecampApproveResponse(result.headcount(), result.status());
    }
}
