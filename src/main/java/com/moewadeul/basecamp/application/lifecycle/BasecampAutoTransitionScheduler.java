package com.moewadeul.basecamp.application.lifecycle;

import com.moewadeul.common.scheduling.ScheduledJobMetrics;
import com.moewadeul.common.time.KoreaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 기본값은 한국 시간 0시부터 0시 50분까지 10분마다다. 첫 실행(0시 정각)이 출발 전날 0시에 확정하거나 취소한다는 정책 시각을 맞춘다.
// 그 실행에서 일시적인 DB 오류나 잠금 대기 시간 초과로 실패한 베이스캠프는 하루를 기다리지 않고 10분 뒤에 다시 시도한다.
// 이미 처리한 베이스캠프는 상태 조건이 맞지 않아 건너뛰므로 다시 돌려도 안전하다.
// cron을 UTC가 아닌 한국 시간으로 해석해야 하므로 zone을 명시한다.
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "moewadeul.basecamp.auto-transition",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BasecampAutoTransitionScheduler {

    private static final String JOB_NAME = "basecamp-auto-transition";

    private final BasecampAutoTransitionService autoTransitionService;
    private final ScheduledJobMetrics scheduledJobMetrics;

    public BasecampAutoTransitionScheduler(
            BasecampAutoTransitionService autoTransitionService, ScheduledJobMetrics scheduledJobMetrics) {
        this.autoTransitionService = autoTransitionService;
        this.scheduledJobMetrics = scheduledJobMetrics;
        scheduledJobMetrics.register(JOB_NAME);
    }

    @Scheduled(cron = "${moewadeul.basecamp.auto-transition.cron:0 0/10 0 * * *}", zone = KoreaTime.ZONE_ID)
    public void transition() {
        try {
            BasecampAutoTransitionService.Result result = autoTransitionService.run();
            // 베이스캠프 하나의 실패는 서비스가 로그만 남기고 계속 처리한다. 그래서 실행이 끝난 뒤 실패가 하나라도 있으면 실패 한 번으로 센다.
            if (result.failed() > 0) {
                scheduledJobMetrics.recordFailure(JOB_NAME);
            }
        } catch (Exception e) {
            // 대상을 읽다가 실패하는 경우처럼 어떤 예외든 이번 실행의 실패 하나로 끝내고 다음 주기에 다시 실행한다.
            // 그래서 작업 이름이 드러나는 ERROR 로그를 직접 남기고 실패 수를 올린다. 예외를 삼키는 곳은 호출 경로의 맨 끝인 여기뿐이다.
            log.error("basecamp auto transition failed", e);
            scheduledJobMetrics.recordFailure(JOB_NAME);
        }
    }
}
