package com.moewadeul.admin.api.program;

import com.moewadeul.program.application.catalog.ProgramCancelResult;

public record AdminProgramCancelResponse(long programId, String status, int canceledApplicationCount) {

    static AdminProgramCancelResponse from(ProgramCancelResult result) {
        return new AdminProgramCancelResponse(result.programId(), result.status(), result.canceledApplicationCount());
    }
}
