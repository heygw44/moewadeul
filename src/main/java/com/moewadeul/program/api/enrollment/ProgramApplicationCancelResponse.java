package com.moewadeul.program.api.enrollment;

import com.moewadeul.program.application.enrollment.ProgramApplicationCancelResult;

public record ProgramApplicationCancelResponse(String status, boolean refunded) {

    static ProgramApplicationCancelResponse from(ProgramApplicationCancelResult result) {
        return new ProgramApplicationCancelResponse(result.status(), result.refunded());
    }
}
