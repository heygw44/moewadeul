package com.moewadeul.admin.api.member;

import com.moewadeul.trust.application.sanction.SanctionLiftResult;

public record SanctionLiftResponse(long sanctionId, String status) {

    static SanctionLiftResponse from(SanctionLiftResult result) {
        return new SanctionLiftResponse(result.sanctionId(), result.status());
    }
}
