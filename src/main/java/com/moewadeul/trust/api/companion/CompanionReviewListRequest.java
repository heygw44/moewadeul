package com.moewadeul.trust.api.companion;

import com.moewadeul.common.paging.PageSlice;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CompanionReviewListRequest(
        @Min(value = 0, message = "페이지 번호는 0 이상이어야 해요.") Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 해요.")
        @Max(value = PageSlice.MAX_SIZE, message = "페이지 크기는 " + PageSlice.MAX_SIZE + " 이하여야 해요.")
        Integer size) {

    int pageOrDefault() {
        return page == null ? PageSlice.DEFAULT_PAGE : page;
    }

    int sizeOrDefault() {
        return size == null ? PageSlice.DEFAULT_SIZE : size;
    }
}
