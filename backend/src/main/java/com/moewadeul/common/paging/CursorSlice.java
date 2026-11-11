package com.moewadeul.common.paging;

import java.util.List;
import java.util.function.Function;

/**
 * 직전 페이지의 마지막 id보다 작은 글을 이어 읽는 커서 방식 페이지다. 건너뛴 행을 읽지 않아 뒤로 갈수록 느려지지 않고,
 * 페이지 사이에 새 글이 들어와도 중복이나 누락이 없다. 조회는 {@link PageSlice#fetchSize}건을 읽고, {@link #of}가
 * size보다 한 건 더 읽혔는지로 hasNext를 정한 뒤 남는 한 건을 자른다.
 */
public record CursorSlice<T>(List<T> content, int size, boolean hasNext, Long nextCursor) {

    public static <T> CursorSlice<T> of(List<T> fetched, int size, Function<? super T, Long> cursorOf) {
        boolean hasNext = fetched.size() > size;
        List<T> content = fetched.stream().limit(size).toList();
        Long nextCursor = hasNext ? cursorOf.apply(content.get(content.size() - 1)) : null;
        return new CursorSlice<>(content, size, hasNext, nextCursor);
    }

    public <R> CursorSlice<R> map(Function<? super T, ? extends R> mapper) {
        return new CursorSlice<>(content.stream().<R>map(mapper).toList(), size, hasNext, nextCursor);
    }
}
