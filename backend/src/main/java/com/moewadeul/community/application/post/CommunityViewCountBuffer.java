package com.moewadeul.community.application.post;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
class CommunityViewCountBuffer {

    private final ConcurrentHashMap<Long, Long> pendingViews = new ConcurrentHashMap<>();

    long add(long postId, long views) {
        return pendingViews.merge(postId, views, Long::sum);
    }

    Map<Long, Long> drain() {
        Map<Long, Long> drained = new HashMap<>();
        for (Long postId : pendingViews.keySet()) {
            Long views = pendingViews.remove(postId);
            if (views != null) {
                drained.put(postId, views);
            }
        }
        return drained;
    }
}
