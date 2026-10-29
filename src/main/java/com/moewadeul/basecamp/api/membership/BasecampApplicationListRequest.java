package com.moewadeul.basecamp.api.membership;

import com.moewadeul.basecamp.application.membership.BasecampApplicationListQuery;
import com.moewadeul.basecamp.domain.BasecampApplicationStatus;
import com.moewadeul.common.paging.PageSlice;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record BasecampApplicationListRequest(
        @Schema(description = "신청 상태. 생략하면 PENDING.") BasecampApplicationStatus status,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 해요.") Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 해요.")
        @Max(value = PageSlice.MAX_SIZE, message = "페이지 크기는 " + PageSlice.MAX_SIZE + " 이하여야 해요.")
        Integer size) {

    BasecampApplicationListQuery toQuery() {
        return new BasecampApplicationListQuery(
                status == null ? BasecampApplicationStatus.PENDING : status,
                page == null ? PageSlice.DEFAULT_PAGE : page,
                size == null ? PageSlice.DEFAULT_SIZE : size);
    }
}
