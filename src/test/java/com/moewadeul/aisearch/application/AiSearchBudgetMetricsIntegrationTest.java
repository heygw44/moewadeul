package com.moewadeul.aisearch.application;

import static com.moewadeul.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.moewadeul.common.testsupport.IntegrationTest;
import com.moewadeul.member.infra.MemberJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class AiSearchBudgetMetricsIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private AiSearchUsageRecorder usageRecorder;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Test
    @DisplayName("[F-27][AI-04] 오늘 비용이 0.5달러이면 예산 사용 비율 지표는 0.5이고, 어제 쓴 토큰은 세지 않는다")
    void gaugeReadsTodaysRatioOnly() {
        // given
        long memberId = memberRepository.saveAndFlush(aMember().build()).getId();
        usageRecorder.claim(memberId, TODAY.minusDays(1));
        usageRecorder.addTokens(memberId, TODAY.minusDays(1), new TokenUsage(2_000_000, 0));
        usageRecorder.claim(memberId, TODAY);
        usageRecorder.addTokens(memberId, TODAY, new TokenUsage(1_250_000, 0));

        // when
        double ratio = meterRegistry
                .get(AiSearchBudgetMetrics.USED_RATIO_METRIC)
                .gauge()
                .value();

        // then
        assertThat(ratio).isCloseTo(0.5, within(1e-9));
    }
}
