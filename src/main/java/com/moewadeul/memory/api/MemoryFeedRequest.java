package com.moewadeul.memory.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record MemoryFeedRequest(
        @Min(value = 0, message = "페이지 번호는 0 이상이어야 해요.") Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 해요.") @Max(value = 50, message = "페이지 크기는 50 이하여야 해요.")
        Integer size) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    int pageOrDefault() {
        return page == null ? DEFAULT_PAGE : page;
    }

    int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }
}
