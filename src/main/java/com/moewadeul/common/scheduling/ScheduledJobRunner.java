package com.moewadeul.common.scheduling;

import java.util.function.IntSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobRunner {

    private final ScheduledJobMetrics scheduledJobMetrics;

    public void register(String job) {
        scheduledJobMetrics.register(job);
    }

    // 예외는 삼킨다. 로그 문구는 작업 이름에서 만든다("review-image-cleanup" -> "review image cleanup failed").
    public void run(String job, Runnable work) {
        try {
            work.run();
        } catch (Exception e) {
            logAndCountFailure(job, e);
        }
    }

    // 항목별 실패를 예외 없이 건수로만 돌려주는 작업용이다. 건수가 0보다 크면 로그 없이 실패를 한 번 센다.
    public void runCountingFailures(String job, IntSupplier work) {
        try {
            if (work.getAsInt() > 0) {
                scheduledJobMetrics.recordFailure(job);
            }
        } catch (Exception e) {
            logAndCountFailure(job, e);
        }
    }

    private void logAndCountFailure(String job, Exception e) {
        log.error("{} failed", job.replace('-', ' '), e);
        scheduledJobMetrics.recordFailure(job);
    }
}
