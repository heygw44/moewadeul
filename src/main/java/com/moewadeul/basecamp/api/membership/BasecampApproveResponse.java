package com.moewadeul.basecamp.api.membership;

import com.moewadeul.basecamp.application.membership.BasecampApproveResult;

public record BasecampApproveResponse(int headcount, String status) {

    static BasecampApproveResponse from(BasecampApproveResult result) {
        return new BasecampApproveResponse(result.headcount(), result.status());
    }
}
