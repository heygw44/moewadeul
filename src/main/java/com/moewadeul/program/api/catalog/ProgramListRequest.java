package com.moewadeul.program.api.catalog;

import com.moewadeul.common.error.EnumParser;
import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.program.application.catalog.ProgramListQuery;
import com.moewadeul.program.domain.ProgramPhase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public record ProgramListRequest(
        @Schema(
                description = "진행 단계. 생략하면 취소되지 않은 행사 모두.",
                allowableValues = {"UPCOMING", "OPEN", "CLOSED"})
        String status,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 해요.") Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 해요.")
        @Max(value = PageSlice.MAX_SIZE, message = "페이지 크기는 " + PageSlice.MAX_SIZE + " 이하여야 해요.")
        Integer size) {

    private static final Set<ProgramPhase> LISTED_PHASES =
            EnumSet.of(ProgramPhase.UPCOMING, ProgramPhase.OPEN, ProgramPhase.CLOSED);

    @Schema(hidden = true)
    @AssertTrue(message = "행사 상태는 신청 예정, 신청 받는 중, 마감 중 하나여야 해요.")
    public boolean isStatusValid() {
        return status == null || listedPhase().isPresent();
    }

    ProgramListQuery toQuery() {
        return new ProgramListQuery(
                listedPhase().orElse(null),
                page == null ? PageSlice.DEFAULT_PAGE : page,
                size == null ? PageSlice.DEFAULT_SIZE : size);
    }

    private Optional<ProgramPhase> listedPhase() {
        return EnumParser.find(ProgramPhase.class, status).filter(LISTED_PHASES::contains);
    }
}
