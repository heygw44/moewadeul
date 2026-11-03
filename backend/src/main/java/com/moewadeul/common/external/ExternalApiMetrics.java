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
    private static final String DEADLINE_COUNTER_NAME = "moewadeul.external.api.deadline";
    private static final String WEATHER_ADMISSION_REJECTED_COUNTER_NAME = "moewadeul.weather.admission.rejected";
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

    public void recordDeadline(String api) {
        Counter.builder(DEADLINE_COUNTER_NAME)
                .description("응답 제한 시간 안에 외부 API 값을 받지 못해 해당 값 없이 응답한 수")
                .tag("api", api)
                .register(meterRegistry)
                .increment();
    }

    public void recordWeatherAdmissionRejected() {
        Counter.builder(WEATHER_ADMISSION_REJECTED_COUNTER_NAME)
                .description("동시에 처리 중인 날씨 요청이 한도에 닿아 외부 API를 기다리지 않고 날씨 없이 응답한 수")
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
