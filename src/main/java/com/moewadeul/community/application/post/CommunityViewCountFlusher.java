package com.moewadeul.community.application.post;

import com.moewadeul.community.infra.post.CommunityPostMapper;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 메모리에 모인 글별 조회 수를 DB에 더한다. 글마다 UPDATE 한 문장이라 트랜잭션을 따로 열지 않고 자동 커밋으로 보낸다.
 *
 * <p>한 글을 더하지 못해도 나머지는 계속 더한다. 더하지 못한 수는 버퍼에 되돌려 다음 회차에 다시 더한다. 그사이 숨겨지거나 지워진 글은 0행이
 * 바뀌고, 그 수는 버린다. 404가 되는 글은 조회수를 세지 않는 규칙과 같다.
 *
 * <p>앱이 정상 종료할 때도 한 번 더한다. 웹 서버가 요청을 다 받고 멈춘 뒤에 빈을 정리하므로, 그때 쌓인 조회까지 DB에 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityViewCountFlusher {

    private final CommunityViewCountBuffer buffer;
    private final CommunityPostMapper communityPostMapper;

    /**
     * 호출하면 쌓인 조회 수를 꺼내 글마다 DB에 더한다.
     *
     * @return 이번에 더하려 한 글 수(실패 포함)
     */
    public int flush() {
        Map<Long, Long> drained = buffer.drain();
        drained.forEach(this::addToPost);
        return drained.size();
    }

    @PreDestroy
    void flushOnShutdown() {
        int posts = flush();
        log.info("community view counts flushed on shutdown posts={}", posts);
    }

    // DB 오류의 종류를 미리 알 수 없고, 한 글의 실패가 다른 글을 막으면 안 된다. 그래서 여기서만 RuntimeException을 통째로 잡고 수를 되돌린다.
    private void addToPost(long postId, long views) {
        try {
            communityPostMapper.addViewCount(postId, views);
        } catch (RuntimeException e) {
            buffer.add(postId, views);
            log.warn("community view count flush failed postId={} views={}", postId, views, e);
        }
    }
}
