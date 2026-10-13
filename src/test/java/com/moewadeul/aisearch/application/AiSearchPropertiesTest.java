package com.moewadeul.aisearch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiSearchPropertiesTest {

    private static final AiSearchProperties DEFAULTS =
            new AiSearchProperties(new BigDecimal("1.00"), new BigDecimal("0.40"), new BigDecimal("1.60"));

    @Test
    @DisplayName("[F-27][AI-04] 입력 250만 토큰이면 비용이 정확히 1달러라서 하루 한도에 닿은 것으로 본다")
    void budgetIsExhaustedAtExactLimit() {
        // given
        TokenTotals atLimit = new TokenTotals(2_500_000, 0);
        TokenTotals belowLimit = new TokenTotals(2_499_999, 0);

        // when & then
        assertThat(DEFAULTS.costUsd(atLimit)).isEqualByComparingTo("1.00");
        assertThat(DEFAULTS.isBudgetExhausted(atLimit)).isTrue();
        assertThat(DEFAULTS.isBudgetExhausted(belowLimit)).isFalse();
    }

    @Test
    @DisplayName("[F-27][AI-04] 입력과 출력 토큰의 비용을 단가대로 더한다")
    void addsInputAndOutputCost() {
        // given
        TokenTotals totals = new TokenTotals(1_000_000, 1_000_000);

        // when & then
        assertThat(DEFAULTS.costUsd(totals)).isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("[F-27][AI-04] 한도나 단가가 비었거나 0 이하이면 설정을 거부한다")
    void rejectsInvalidSettings() {
        // when & then
        assertThatThrownBy(() -> new AiSearchProperties(null, BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiSearchProperties(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiSearchProperties(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
