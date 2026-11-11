package com.moewadeul.common.web;

import com.moewadeul.common.paging.CursorSlice;
import java.util.List;
import java.util.function.Function;

public record CursorResponse<T>(List<T> content, int size, boolean hasNext, Long nextCursor) {

    public static <S, T> CursorResponse<T> from(CursorSlice<S> slice, Function<? super S, ? extends T> mapper) {
        CursorSlice<T> mapped = slice.map(mapper);
        return new CursorResponse<>(mapped.content(), mapped.size(), mapped.hasNext(), mapped.nextCursor());
    }
}
