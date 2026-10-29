package com.moewadeul.program.api.enrollment;

import com.moewadeul.program.application.enrollment.ProgramPayResult;
import com.moewadeul.program.domain.ProgramApplicationStatus;
import java.time.Instant;

public record ProgramPayResponse(long applicationId, ProgramApplicationStatus status, Instant paidAt) {

    static ProgramPayResponse from(ProgramPayResult result) {
        return new ProgramPayResponse(result.applicationId(), result.status(), result.paidAt());
    }
}
