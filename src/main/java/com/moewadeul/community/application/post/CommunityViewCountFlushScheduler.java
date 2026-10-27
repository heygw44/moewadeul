package com.moewadeul.community.application.post;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "moewadeul.community.view-count",
        name = "flush-enabled",
        havingValue = "true",
        matchIfMissing = true)
class CommunityViewCountFlushScheduler {

    static final String DEFAULT_FLUSH_DELAY = "2s";

    private final CommunityViewCountFlusher flusher;

    @Scheduled(fixedDelayString = "${moewadeul.community.view-count.flush-delay:" + DEFAULT_FLUSH_DELAY + "}")
    void flush() {
        flusher.flush();
    }
}
