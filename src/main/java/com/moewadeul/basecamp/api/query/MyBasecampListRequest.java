package com.moewadeul.basecamp.api.query;

import com.moewadeul.basecamp.application.query.MyBasecampListQuery;
import com.moewadeul.basecamp.domain.BasecampRelation;
import com.moewadeul.basecamp.domain.BasecampStatus;
import com.moewadeul.common.paging.PageSlice;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record MyBasecampListRequest(
        @Schema(description = "내 관계. 생략하면 셋 모두.") Relation relation,

        @Schema(description = "베이스캠프 상태. 생략하면 모든 상태.") BasecampStatus status,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 해요.") Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 해요.")
        @Max(value = PageSlice.MAX_SIZE, message = "페이지 크기는 " + PageSlice.MAX_SIZE + " 이하여야 해요.")
        Integer size) {

    public enum Relation {
        LEADER,
        MEMBER,
        APPLICANT
    }

    MyBasecampListQuery toQuery() {
        return new MyBasecampListQuery(
                relation == null ? null : BasecampRelation.valueOf(relation.name()),
                status,
                page == null ? PageSlice.DEFAULT_PAGE : page,
                size == null ? PageSlice.DEFAULT_SIZE : size);
    }
}
