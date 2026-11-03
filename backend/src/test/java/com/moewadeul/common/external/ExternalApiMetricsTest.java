package com.moewadeul.common.external;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExternalApiMetricsTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ExternalApiMetrics metrics = new ExternalApiMetrics(meterRegistry);

    @Test
    @DisplayName("[NFR-05] 응답 제한 시간을 넘겨 값을 빼고 응답한 수를 api 태그별로 센다")
    void countsDeadlinesPerApi() {
        metrics.recordDeadline("kma");
        metrics.recordDeadline("kma");
        metrics.recordDeadline("kasi");

        assertThat(meterRegistry
                        .get("moewadeul.external.api.deadline")
                        .tag("api", "kma")
                        .counter()
                        .count())
                .isEqualTo(2);
        assertThat(meterRegistry
                        .get("moewadeul.external.api.deadline")
                        .tag("api", "kasi")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[NFR-05] 날씨 요청 입장 제한에 걸려 거부한 수를 센다")
    void countsWeatherAdmissionRejections() {
        metrics.recordWeatherAdmissionRejected();
        metrics.recordWeatherAdmissionRejected();

        assertThat(meterRegistry
                        .get("moewadeul.weather.admission.rejected")
                        .counter()
                        .count())
                .isEqualTo(2);
    }
}
