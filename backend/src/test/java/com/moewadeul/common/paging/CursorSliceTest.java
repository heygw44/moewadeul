package com.moewadeul.common.paging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CursorSliceTest {

    @Test
    @DisplayName("size보다 한 건 더 읽혔으면 hasNext가 true이고, 남긴 마지막 항목의 id가 nextCursor이다")
    void extraRowMeansNextPageAndCursorIsLastKeptItem() {
        // when
        CursorSlice<Long> slice = CursorSlice.of(List.of(30L, 20L, 10L), 2, id -> id);

        // then
        assertThat(slice.content()).containsExactly(30L, 20L);
        assertThat(slice.size()).isEqualTo(2);
        assertThat(slice.hasNext()).isTrue();
        assertThat(slice.nextCursor()).isEqualTo(20L);
    }

    @Test
    @DisplayName("정확히 size건만 읽혔거나 비어 있으면 hasNext가 false이고 nextCursor가 없다")
    void lastPageHasNoCursor() {
        // when
        CursorSlice<Long> exact = CursorSlice.of(List.of(30L, 20L), 2, id -> id);
        CursorSlice<Long> empty = CursorSlice.of(List.of(), 20, id -> id);

        // then
        assertThat(exact.content()).containsExactly(30L, 20L);
        assertThat(exact.hasNext()).isFalse();
        assertThat(exact.nextCursor()).isNull();
        assertThat(empty.content()).isEmpty();
        assertThat(empty.hasNext()).isFalse();
        assertThat(empty.nextCursor()).isNull();
    }

    @Test
    @DisplayName("map은 항목만 바꾸고 size, hasNext, nextCursor를 그대로 둔다")
    void mapKeepsPagingFields() {
        // when
        CursorSlice<String> mapped =
                CursorSlice.of(List.of(3L, 2L, 1L), 2, id -> id).map(id -> "글" + id);

        // then
        assertThat(mapped.content()).containsExactly("글3", "글2");
        assertThat(mapped.size()).isEqualTo(2);
        assertThat(mapped.hasNext()).isTrue();
        assertThat(mapped.nextCursor()).isEqualTo(2L);
    }
}
