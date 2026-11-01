package com.moewadeul.aisearch.application;

import com.moewadeul.common.time.KoreaTime;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiSearchBudgetMetrics {

    static final String USED_RATIO_METRIC = "moewadeul.aisearch.budget.used.ratio";

    private final AiSearchUsageRecorder usageRecorder;
    private final AiSearchProperties properties;
    private final Clock clock;

    public AiSearchBudgetMetrics(
            AiSearchUsageRecorder usageRecorder,
            AiSearchProperties properties,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.usageRecorder = usageRecorder;
        this.properties = properties;
        this.clock = clock;
        Gauge.builder(USED_RATIO_METRIC, this, AiSearchBudgetMetrics::readUsedRatio)
                .description("오늘 AI 검색 비용 / 하루 예산")
                .register(meterRegistry);
    }

    private double readUsedRatio() {
        try {
            return properties.usedRatio(usageRecorder.sumTokens(KoreaTime.today(clock)));
        } catch (DataAccessException e) {
            log.warn("ai search budget ratio read failed", e);
            return Double.NaN;
        }
    }
}
