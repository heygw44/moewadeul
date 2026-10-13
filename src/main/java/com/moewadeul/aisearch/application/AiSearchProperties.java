package com.moewadeul.aisearch.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI 검색의 하루 비용 한도와 토큰 단가.
 *
 * @param dailyBudgetUsd 서비스 전체가 하루(한국 날짜)에 쓸 수 있는 OpenAI 비용 상한(달러). 이 금액에 닿으면 그날은 호출하지 않는다.
 * @param inputPricePerMillionUsd 입력 토큰 100만 개의 가격(달러)
 * @param outputPricePerMillionUsd 출력 토큰 100만 개의 가격(달러)
 */
@ConfigurationProperties("moewadeul.aisearch")
public record AiSearchProperties(
        @DefaultValue("1.00") BigDecimal dailyBudgetUsd,
        @DefaultValue("0.40") BigDecimal inputPricePerMillionUsd,
        @DefaultValue("1.60") BigDecimal outputPricePerMillionUsd) {

    private static final String PREFIX = "moewadeul.aisearch.";
    private static final BigDecimal TOKENS_PER_PRICE_UNIT = BigDecimal.valueOf(1_000_000);
    private static final int COST_SCALE = 8;

    public AiSearchProperties {
        requirePositive(dailyBudgetUsd, "dailyBudgetUsd");
        requirePositive(inputPricePerMillionUsd, "inputPricePerMillionUsd");
        requirePositive(outputPricePerMillionUsd, "outputPricePerMillionUsd");
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(PREFIX + name + " must be positive");
        }
    }

    /** 호출하면 토큰 합계의 비용(달러)을 돌려준다. 한도를 넘기지 않게 소수 여덟째 자리에서 올린다. */
    public BigDecimal costUsd(TokenTotals totals) {
        BigDecimal input = inputPricePerMillionUsd.multiply(BigDecimal.valueOf(totals.inputTokens()));
        BigDecimal output = outputPricePerMillionUsd.multiply(BigDecimal.valueOf(totals.outputTokens()));
        return input.add(output).divide(TOKENS_PER_PRICE_UNIT, COST_SCALE, RoundingMode.UP);
    }

    /** 호출하면 그날 쓴 비용을 하루 한도로 나눈 값을 돌려준다. 0.5는 한도의 절반을 썼다는 뜻이고, 한도를 넘으면 1보다 크다. */
    public double usedRatio(TokenTotals totals) {
        return costUsd(totals)
                .divide(dailyBudgetUsd, COST_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** 호출하면 그날 쓴 비용이 하루 한도에 닿았는지 돌려준다. 한도와 같아도 닿은 것으로 본다. */
    public boolean isBudgetExhausted(TokenTotals totals) {
        return costUsd(totals).compareTo(dailyBudgetUsd) >= 0;
    }
}
