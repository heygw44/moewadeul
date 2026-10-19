package com.moewadeul.community.application.post;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// fixedDelay는 이전 회차가 끝난 뒤부터 간격을 센다. 그래서 같은 서버에서 두 회차가 겹치지 않는다.
// 서버가 여러 대여도 각자 자기 메모리의 수만 더하고, 더하기는 view_count = view_count + n이라 겹쳐도 된다.
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "moewadeul.community.view-count",
        name = "flush-enabled",
        havingValue = "true",
        matchIfMissing = true)
class CommunityViewCountFlushScheduler {

    /** 조회 수를 DB에 더하는 간격. 목록과 다른 서버에 조회수가 이만큼 늦게 보이고, 비정상 종료하면 이만큼의 조회를 잃을 수 있다. */
    static final String DEFAULT_FLUSH_DELAY = "2s";

    private final CommunityViewCountFlusher flusher;

    @Scheduled(fixedDelayString = "${moewadeul.community.view-count.flush-delay:" + DEFAULT_FLUSH_DELAY + "}")
    void flush() {
        flusher.flush();
    }
}
