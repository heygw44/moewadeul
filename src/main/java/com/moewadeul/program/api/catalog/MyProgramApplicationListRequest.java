package com.moewadeul.program.api.catalog;

import com.moewadeul.common.error.EnumParser;
import com.moewadeul.common.paging.PageSlice;
import com.moewadeul.program.application.catalog.MyProgramApplicationQuery;
import com.moewadeul.program.domain.ProgramApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record MyProgramApplicationListRequest(
        @Schema(implementation = ProgramApplicationStatus.class, description = "신청 상태. 생략하면 모든 상태.")
        String status,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 해요.") Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 해요.")
        @Max(value = PageSlice.MAX_SIZE, message = "페이지 크기는 " + PageSlice.MAX_SIZE + " 이하여야 해요.")
        Integer size) {

    @Schema(hidden = true)
    @AssertTrue(message = "신청 상태는 결제 대기, 확정, 취소, 만료 중 하나여야 해요.")
    public boolean isStatusValid() {
        return status == null
                || EnumParser.find(ProgramApplicationStatus.class, status).isPresent();
    }

    MyProgramApplicationQuery toQuery() {
        return new MyProgramApplicationQuery(
                EnumParser.find(ProgramApplicationStatus.class, status).orElse(null),
                page == null ? PageSlice.DEFAULT_PAGE : page,
                size == null ? PageSlice.DEFAULT_SIZE : size);
    }
}
