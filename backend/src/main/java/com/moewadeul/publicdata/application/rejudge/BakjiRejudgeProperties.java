package com.moewadeul.publicdata.application.rejudge;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("moewadeul.publicdata.bakji-rejudge")
public record BakjiRejudgeProperties(
        @DefaultValue("30m") Duration staleAfter,
        @DefaultValue("1000") int chunkSize) {

    public BakjiRejudgeProperties {
        if (staleAfter.isZero() || staleAfter.isNegative()) {
            throw new IllegalArgumentException("moewadeul.publicdata.bakji-rejudge.staleAfter must be positive");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("moewadeul.publicdata.bakji-rejudge.chunkSize must be positive");
        }
    }
}
