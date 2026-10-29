package com.moewadeul.common.web;

import com.moewadeul.common.paging.PageSlice;
import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> content, int page, int size, boolean hasNext) {

    public static <S, T> PageResponse<T> from(PageSlice<S> slice, Function<? super S, ? extends T> mapper) {
        PageSlice<T> mapped = slice.map(mapper);
        return new PageResponse<>(mapped.content(), mapped.page(), mapped.size(), mapped.hasNext());
    }
}
