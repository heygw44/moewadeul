package com.moewadeul.aisearch.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * 오늘 AI 검색 비용이 하루 예산의 얼마인지(0~1, 넘으면 1 초과)를 지표로 내보낸다.
 *
 * <p>운영자가 예산에 닿기 전에 알림을 받으려는 지표다. 값은 지표를 긁어 갈 때마다 DB에서 오늘 토큰 합계를 읽어 계산한다.
 * 그래서 날짜가 바뀌어도 따로 상태를 들고 있거나 초기화할 필요 없이 새 날짜의 값이 바로 나온다.
 */
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

    // DB가 잠깐 막혔다고 지표를 긁어 가는 요청 전체가 실패하면 다른 지표까지 빠진다. 그래서 읽지 못하면 NaN으로 두고
    // 알림이 값 없음으로 처리하게 한다.
    private double readUsedRatio() {
        try {
            return properties.usedRatio(usageRecorder.sumTokens(AiSearchDay.today(clock)));
        } catch (DataAccessException e) {
            log.warn("ai search budget ratio read failed", e);
            return Double.NaN;
        }
    }
}
