package com.moewadeul.notification.api.inbox;

import com.moewadeul.common.paging.PageSlice;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record NotificationListRequest(
        @Schema(
                description =
                        "페이지 번호. 0부터 시작하고 생략하면 0이다. 건너뛸 건수(page × size)가 int 범위를 넘지 않도록 " + MAX_PAGE + " 이하여야 한다.")
        @Min(value = 0, message = "페이지 번호는 0 이상이어야 해요.")
        @Max(value = MAX_PAGE, message = "페이지 번호는 " + MAX_PAGE + " 이하여야 해요.")
        Integer page,

        @Schema(description = "페이지 크기. 1~50이고 생략하면 20이다.")
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 해요.")
        @Max(value = PageSlice.MAX_SIZE, message = "페이지 크기는 " + PageSlice.MAX_SIZE + " 이하여야 해요.")
        Integer size) {

    private static final int MAX_PAGE = Integer.MAX_VALUE / PageSlice.MAX_SIZE;

    int pageOrDefault() {
        return page == null ? PageSlice.DEFAULT_PAGE : page;
    }

    int sizeOrDefault() {
        return size == null ? PageSlice.DEFAULT_SIZE : size;
    }
}
