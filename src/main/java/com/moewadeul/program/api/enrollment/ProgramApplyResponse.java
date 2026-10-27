package com.moewadeul.program.api.enrollment;

import com.moewadeul.program.application.enrollment.ProgramApplyResult;
import java.time.Instant;

public record ProgramApplyResponse(long applicationId, String status, Instant paymentDueAt, int amount) {

    static ProgramApplyResponse from(ProgramApplyResult result) {
        return new ProgramApplyResponse(
                result.applicationId(), result.status(), result.paymentDueAt(), result.amount());
    }
}
