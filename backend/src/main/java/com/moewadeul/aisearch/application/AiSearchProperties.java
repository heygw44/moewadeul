package com.moewadeul.aisearch.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

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

    public BigDecimal costUsd(TokenTotals totals) {
        BigDecimal input = inputPricePerMillionUsd.multiply(BigDecimal.valueOf(totals.inputTokens()));
        BigDecimal output = outputPricePerMillionUsd.multiply(BigDecimal.valueOf(totals.outputTokens()));
        return input.add(output).divide(TOKENS_PER_PRICE_UNIT, COST_SCALE, RoundingMode.UP);
    }

    public double usedRatio(TokenTotals totals) {
        return costUsd(totals)
                .divide(dailyBudgetUsd, COST_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public boolean isBudgetExhausted(TokenTotals totals) {
        return costUsd(totals).compareTo(dailyBudgetUsd) >= 0;
    }
}
