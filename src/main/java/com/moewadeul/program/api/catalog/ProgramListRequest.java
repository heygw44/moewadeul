package com.moewadeul.program.api.catalog;

import com.moewadeul.program.application.catalog.ProgramListQuery;
import com.moewadeul.program.domain.ProgramPhase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ProgramListRequest(
        @Schema(
                description = "진행 단계. 생략하면 취소되지 않은 행사 모두.",
                allowableValues = {"UPCOMING", "OPEN", "CLOSED"})
        String status,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 해요.") Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 해요.") @Max(value = 50, message = "페이지 크기는 50 이하여야 해요.")
        Integer size) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    @Schema(hidden = true)
    @AssertTrue(message = "status는 UPCOMING, OPEN, CLOSED 중 하나여야 해요.")
    public boolean isStatusValid() {
        return status == null
                || status.equals(ProgramPhase.UPCOMING.name())
                || status.equals(ProgramPhase.OPEN.name())
                || status.equals(ProgramPhase.CLOSED.name());
    }

    ProgramListQuery toQuery() {
        ProgramPhase phase = status == null ? null : ProgramPhase.valueOf(status);
        return new ProgramListQuery(phase, page == null ? DEFAULT_PAGE : page, size == null ? DEFAULT_SIZE : size);
    }
}
