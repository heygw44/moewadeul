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
        return flushAll().failed();
    }

    @PreDestroy
    void flushOnShutdown() {
        FlushResult result = flushAll();
        if (result.failed() > 0) {
            log.error(
                    "community view counts left unflushed on shutdown flushed={} failed={}",
                    result.flushed(),
                    result.failed());
        } else {
            log.info("community view counts flushed on shutdown posts={}", result.flushed());
        }
    }

    private FlushResult flushAll() {
        int flushed = 0;
        int failed = 0;
        for (Map.Entry<Long, Long> entry : buffer.drain().entrySet()) {
            if (addToPost(entry.getKey(), entry.getValue())) {
                flushed++;
            } else {
                failed++;
            }
        }
        return new FlushResult(flushed, failed);
    }

    private boolean addToPost(long postId, long views) {
        try {
            communityPostMapper.addViewCount(postId, views);
            return true;
        } catch (RuntimeException e) {
            buffer.add(postId, views);
            log.warn("community view count flush failed postId={} views={}", postId, views, e);
            return false;
        }
    }

    private record FlushResult(int flushed, int failed) {}
}
