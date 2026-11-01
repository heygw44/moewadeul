package com.moewadeul.community.application.post;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "moewadeul.community.view-count",
        name = "flush-enabled",
        havingValue = "true",
        matchIfMissing = true)
class CommunityViewCountFlushScheduler {

    static final String DEFAULT_FLUSH_DELAY = "2s";

    private static final String JOB_NAME = "community-view-count-flush";

    private final CommunityViewCountFlusher flusher;
    private final ScheduledJobRunner jobRunner;

    CommunityViewCountFlushScheduler(CommunityViewCountFlusher flusher, ScheduledJobRunner jobRunner) {
        this.flusher = flusher;
        this.jobRunner = jobRunner;
        jobRunner.register(JOB_NAME);
    }

    @Scheduled(fixedDelayString = "${moewadeul.community.view-count.flush-delay:" + DEFAULT_FLUSH_DELAY + "}")
    void flush() {
        jobRunner.runCountingFailures(JOB_NAME, flusher::flush);
    }
}
