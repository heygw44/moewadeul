package com.moewadeul.aisearch.application;

import com.moewadeul.common.metrics.PolledMetric;
import com.moewadeul.common.metrics.PolledSnapshot;
import com.moewadeul.common.time.KoreaTime;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class AiSearchBudgetMetrics implements PolledMetric {

    static final String USED_RATIO_METRIC = "moewadeul.aisearch.budget.used.ratio";

    private final AiSearchUsageRecorder usageRecorder;
    private final AiSearchProperties properties;
    private final Clock clock;
    private final PolledSnapshot<Double> usedRatio;

    public AiSearchBudgetMetrics(
            AiSearchUsageRecorder usageRecorder,
            AiSearchProperties properties,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.usageRecorder = usageRecorder;
        this.properties = properties;
        this.clock = clock;
        this.usedRatio = new PolledSnapshot<>(this::readUsedRatio, clock);
        Gauge.builder(USED_RATIO_METRIC, usedRatio, snapshot -> snapshot.read(Double::doubleValue))
                .description("오늘 AI 검색 비용 / 하루 예산")
                .register(meterRegistry);
    }

    @Override
    public String job() {
        return "ai-search-budget-metrics-refresh";
    }

    @Override
    public void refresh() {
        usedRatio.refresh();
    }

    private double readUsedRatio() {
        return properties.usedRatio(usageRecorder.sumTokens(KoreaTime.today(clock)));
    }
}
