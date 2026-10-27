package com.moewadeul.program.api.enrollment;

import com.moewadeul.program.application.enrollment.ProgramPayResult;
import java.time.Instant;

public record ProgramPayResponse(long applicationId, String status, Instant paidAt) {

    static ProgramPayResponse from(ProgramPayResult result) {
        return new ProgramPayResponse(result.applicationId(), result.status(), result.paidAt());
    }
}
