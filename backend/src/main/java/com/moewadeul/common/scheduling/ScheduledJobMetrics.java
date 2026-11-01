package com.moewadeul.common.scheduling;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduledJobMetrics {

    private static final String FAILURE_COUNTER_NAME = "moewadeul.scheduled.job.failures";

    private final MeterRegistry meterRegistry;

    // Prometheus는 처음 나타난 시계열의 첫 값을 증가로 세지 않는다. 0으로 먼저 만들어 두지 않으면 앱 시작 뒤 첫 실패가 알림에서 빠진다.
    public void register(String job) {
        failureCounter(job);
    }

    public void recordFailure(String job) {
        failureCounter(job).increment();
    }

    private Counter failureCounter(String job) {
        return Counter.builder(FAILURE_COUNTER_NAME)
                .description("스케줄 작업이 예외로 끝난 횟수")
                .tag("job", job)
                .register(meterRegistry);
    }
}
