package com.moewadeul.common.paging;

import java.util.List;
import java.util.function.Function;

/**
 * 전체 개수 없이 다음 페이지가 있는지만 아는 페이지다. 조회는 {@link #offsetOf}부터 {@link #fetchSize}건을 읽고,
 * {@link #of}가 size보다 한 건 더 읽혔는지로 hasNext를 정한 뒤 남는 한 건을 자른다.
 */
public record PageSlice<T>(List<T> content, int page, int size, boolean hasNext) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    public static long offsetOf(int page, int size) {
        return (long) page * size;
    }

    public static int fetchSize(int size) {
        return size + 1;
    }

    public static <T> PageSlice<T> of(List<T> fetched, int page, int size) {
        boolean hasNext = fetched.size() > size;
        return new PageSlice<>(fetched.stream().limit(size).toList(), page, size, hasNext);
    }

    public <R> PageSlice<R> map(Function<? super T, ? extends R> mapper) {
        return new PageSlice<>(content.stream().<R>map(mapper).toList(), page, size, hasNext);
    }
}
