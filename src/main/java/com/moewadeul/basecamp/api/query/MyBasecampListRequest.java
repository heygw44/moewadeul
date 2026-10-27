package com.moewadeul.basecamp.api.query;

import com.moewadeul.basecamp.application.query.MyBasecampListQuery;
import com.moewadeul.basecamp.domain.BasecampRelation;
import com.moewadeul.basecamp.domain.BasecampStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record MyBasecampListRequest(
        @Schema(description = "내 관계. LEADER, MEMBER, APPLICANT 중 하나. 생략하면 셋 모두.")
        BasecampRelation relation,

        @Schema(description = "베이스캠프 상태. 생략하면 모든 상태.") BasecampStatus status,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 해요.") Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 해요.") @Max(value = 50, message = "페이지 크기는 50 이하여야 해요.")
        Integer size) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    @AssertTrue(message = "relation은 LEADER, MEMBER, APPLICANT 중 하나여야 해요.")
    public boolean isRelationValid() {
        return relation != BasecampRelation.NONE;
    }

    MyBasecampListQuery toQuery() {
        return new MyBasecampListQuery(
                relation, status, page == null ? DEFAULT_PAGE : page, size == null ? DEFAULT_SIZE : size);
    }
}
