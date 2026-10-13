package com.moewadeul.aisearch.application;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 기본값은 매일 새벽 4시 20분(한국 시간)이다. 사용자가 적은 시간에 돌리되, 같은 새벽에 도는 다른 정리 작업과 겹치지 않게 한다.
// cron을 UTC가 아닌 한국 시간으로 해석해야 하므로 zone을 명시한다.
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.aisearch.usage-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AiSearchUsageCleanupScheduler {

    private static final String JOB_NAME = "ai-search-usage-cleanup";

    private final AiSearchUsageCleanupService cleanupService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public AiSearchUsageCleanupScheduler(
            AiSearchUsageCleanupService cleanupService, ScheduledJobMetrics scheduledJobMetrics) {
        this.cleanupService = cleanupService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.aisearch.usage-cleanup.cron:0 20 4 * * *}", zone = "Asia/Seoul")
    public void cleanUp() {
        try {
            cleanupService.deleteExpiredUsage();
        } catch (Exception e) {
            // 어떤 예외든 이번 실행의 실패 하나로 끝내고 다음 주기에 다시 실행한다. 예외를 스케줄러 기본 처리기에 맡기면
            // 어느 작업이 실패했는지 로그만 봐서는 알기 어렵다. 그래서 작업 이름이 드러나는 ERROR 로그를 직접 남긴다.
            // 예외를 삼키는 곳은 호출 경로의 맨 끝인 여기뿐이다.
            log.error("ai search usage cleanup failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
