package com.moewadeul.program.api.enrollment;

import com.moewadeul.program.application.enrollment.ProgramApplicationCancelResult;
import com.moewadeul.program.domain.ProgramApplicationStatus;

public record ProgramApplicationCancelResponse(ProgramApplicationStatus status, boolean refunded) {

    static ProgramApplicationCancelResponse from(ProgramApplicationCancelResult result) {
        return new ProgramApplicationCancelResponse(result.status(), result.refunded());
    }
}
