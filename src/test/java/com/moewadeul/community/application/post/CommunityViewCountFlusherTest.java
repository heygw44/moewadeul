package com.moewadeul.community.application.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moewadeul.community.infra.post.CommunityPostMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

class CommunityViewCountFlusherTest {

    private final CommunityViewCountBuffer buffer = new CommunityViewCountBuffer();
    private final CommunityPostMapper mapper = mock(CommunityPostMapper.class);
    private final CommunityViewCountFlusher flusher = new CommunityViewCountFlusher(buffer, mapper);

    @Test
    @DisplayName("[CM-11] 쌓인 조회 수를 글마다 UPDATE 한 번으로 더한다")
    void addsAccumulatedViewsOncePerPost() {
        buffer.add(1L, 1);
        buffer.add(1L, 1);
        buffer.add(2L, 1);

        int failed = flusher.flush();

        assertThat(failed).isZero();
        verify(mapper).addViewCount(1L, 2L);
        verify(mapper).addViewCount(2L, 1L);
        assertThat(buffer.drain()).isEmpty();
    }

    @Test
    @DisplayName("[CM-11] DB에 더하지 못한 글의 조회 수는 버퍼에 되돌려 다음 회차에 더하고, 다른 글은 계속 더한다")
    void failedPostKeepsViewsForNextFlush() {
        when(mapper.addViewCount(1L, 2L)).thenThrow(new QueryTimeoutException("simulated"));
        buffer.add(1L, 2);
        buffer.add(2L, 1);

        int failed = flusher.flush();

        assertThat(failed).isEqualTo(1);
        verify(mapper).addViewCount(2L, 1L);
        assertThat(buffer.drain()).containsEntry(1L, 2L).doesNotContainKey(2L);
    }

    @Test
    @DisplayName("[CM-11] 앱이 정상 종료할 때 남은 조회 수를 더한다")
    void flushesOnShutdown() {
        when(mapper.addViewCount(anyLong(), anyLong())).thenReturn(1);
        buffer.add(3L, 4);

        flusher.flushOnShutdown();

        verify(mapper).addViewCount(3L, 4L);
    }
}
