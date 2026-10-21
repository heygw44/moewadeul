package com.moewadeul.member.application.verification;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.time.KoreaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 기본값은 매일 새벽 5시 40분(한국 시간)이다. 다른 정리 작업(4시 정각~4시 50분, 5시 정각~5시 30분)과 겹치지 않게 한다.
// cron을 UTC가 아닌 한국 시간으로 해석해야 하므로 zone을 명시한다.
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.member.email-verification-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class EmailVerificationCleanupScheduler {

    private static final String JOB_NAME = "email-verification-cleanup";

    private final EmailVerificationCleanupService cleanupService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public EmailVerificationCleanupScheduler(
            EmailVerificationCleanupService cleanupService, ScheduledJobMetrics scheduledJobMetrics) {
        this.cleanupService = cleanupService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.member.email-verification-cleanup.cron:0 40 5 * * *}", zone = KoreaTime.ZONE_ID)
    public void cleanUp() {
        try {
            cleanupService.deleteExpired();
        } catch (Exception e) {
            // 어떤 예외든 이번 실행의 실패 하나로 끝내고 다음 주기에 다시 실행한다. 작업 이름이 드러나는 ERROR 로그를 직접 남기고
            // 실패 지표를 올린다. 예외를 삼키는 곳은 호출 경로의 맨 끝인 여기뿐이다.
            log.error("email verification cleanup failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
