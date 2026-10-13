package com.moewadeul.admin.api;

import com.moewadeul.program.application.catalog.ProgramCancelResult;

/** 행사를 취소한 결과다. canceledApplicationCount는 함께 취소된 결제 대기·확정 신청 수다. */
public record AdminProgramCancelResponse(long programId, String status, int canceledApplicationCount) {

    static AdminProgramCancelResponse from(ProgramCancelResult result) {
        return new AdminProgramCancelResponse(result.programId(), result.status(), result.canceledApplicationCount());
    }
}
