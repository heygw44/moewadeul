package com.moewadeul.common.paging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PageSliceTest {

    @Test
    @DisplayName("size보다 한 건 더 읽혔으면 hasNext가 true이고 size건만 남긴다")
    void extraRowMeansNextPageAndIsTrimmed() {
        // given
        List<Integer> fetched = List.of(1, 2, 3);

        // when
        PageSlice<Integer> slice = PageSlice.of(fetched, 4, 2);

        // then
        assertThat(slice.content()).containsExactly(1, 2);
        assertThat(slice.page()).isEqualTo(4);
        assertThat(slice.size()).isEqualTo(2);
        assertThat(slice.hasNext()).isTrue();
    }

    @Test
    @DisplayName("정확히 size건만 읽혔으면 hasNext가 false이고 그대로 둔다")
    void exactSizeHasNoNextPage() {
        // when
        PageSlice<Integer> slice = PageSlice.of(List.of(1, 2), 0, 2);

        // then
        assertThat(slice.content()).containsExactly(1, 2);
        assertThat(slice.hasNext()).isFalse();
    }

    @Test
    @DisplayName("읽힌 행이 없으면 빈 목록이고 hasNext가 false이다")
    void emptyFetchIsEmptyLastPage() {
        // when
        PageSlice<Integer> slice = PageSlice.of(List.of(), 3, 20);

        // then
        assertThat(slice.content()).isEmpty();
        assertThat(slice.hasNext()).isFalse();
    }

    @Test
    @DisplayName("map은 항목만 바꾸고 page, size, hasNext를 그대로 둔다")
    void mapKeepsPagingFields() {
        // given
        PageSlice<Integer> slice = new PageSlice<>(List.of(1, 2), 2, 5, true);

        // when
        PageSlice<String> mapped = slice.map(value -> "v" + value);

        // then
        assertThat(mapped).isEqualTo(new PageSlice<>(List.of("v1", "v2"), 2, 5, true));
    }

    @Test
    @DisplayName("조회 크기는 size보다 하나 크고 offset은 int 범위를 넘어도 넘치지 않는다")
    void fetchSizeAndOffset() {
        assertThat(PageSlice.fetchSize(20)).isEqualTo(21);
        assertThat(PageSlice.offsetOf(3, 20)).isEqualTo(60L);
        assertThat(PageSlice.offsetOf(Integer.MAX_VALUE, 50)).isEqualTo((long) Integer.MAX_VALUE * 50);
    }
}
