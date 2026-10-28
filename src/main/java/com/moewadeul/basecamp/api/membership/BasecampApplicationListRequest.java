package com.moewadeul.basecamp.api.membership;

import com.moewadeul.basecamp.application.membership.BasecampApplicationListQuery;
import com.moewadeul.basecamp.domain.BasecampApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record BasecampApplicationListRequest(
        @Schema(description = "신청 상태. 생략하면 PENDING.") BasecampApplicationStatus status,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 해요.") Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 해요.") @Max(value = 50, message = "페이지 크기는 50 이하여야 해요.")
        Integer size) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    BasecampApplicationListQuery toQuery() {
        return new BasecampApplicationListQuery(
                status == null ? BasecampApplicationStatus.PENDING : status,
                page == null ? DEFAULT_PAGE : page,
                size == null ? DEFAULT_SIZE : size);
    }
}
