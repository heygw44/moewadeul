package com.moewadeul.community.application.post;

import com.moewadeul.community.infra.post.CommunityPostMapper;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityViewCountFlusher {

    private final CommunityViewCountBuffer buffer;
    private final CommunityPostMapper communityPostMapper;

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

    private void addToPost(long postId, long views) {
        try {
            communityPostMapper.addViewCount(postId, views);
        } catch (RuntimeException e) {
            buffer.add(postId, views);
            log.warn("community view count flush failed postId={} views={}", postId, views, e);
        }
    }
}
