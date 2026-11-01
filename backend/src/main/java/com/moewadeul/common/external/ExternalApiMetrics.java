package com.moewadeul.common.external;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExternalApiMetrics {

    private static final String TIMER_NAME = "moewadeul.external.api.requests";
    private static final String FALLBACK_COUNTER_NAME = "moewadeul.external.api.fallbacks";
    private static final String REJECTED_COUNTER_NAME = "moewadeul.external.api.rejected";
    private static final String SKIPPED_COUNTER_NAME = "moewadeul.external.api.skipped";
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";

    private final MeterRegistry meterRegistry;

    public <T> T record(String api, String operation, Supplier<T> call) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = FAILURE;
        try {
            T result = call.get();
            outcome = SUCCESS;
            return result;
        } finally {
            sample.stop(timer(api, operation, outcome));
        }
    }

    public void recordFallback(String api) {
        Counter.builder(FALLBACK_COUNTER_NAME)
                .description("외부 API 실패로 해당 값 없이 응답한 수")
                .tag("api", api)
                .register(meterRegistry)
                .increment();
    }

    public void recordRejected(String api) {
        Counter.builder(REJECTED_COUNTER_NAME)
                .description("동시 호출 수 한도에 닿아 외부 API를 부르지 않고 거부한 수")
                .tag("api", api)
                .register(meterRegistry)
                .increment();
    }

    public void recordSkipped(String api) {
        Counter.builder(SKIPPED_COUNTER_NAME)
                .description("외부 API 장애로 쉬는 동안 호출하지 않고 건너뛴 수")
                .tag("api", api)
                .register(meterRegistry)
                .increment();
    }

    private Timer timer(String api, String operation, String outcome) {
        return Timer.builder(TIMER_NAME)
                .description("외부 API에 보낸 HTTP 요청 수와 걸린 시간")
                .tag("api", api)
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(meterRegistry);
    }
}
