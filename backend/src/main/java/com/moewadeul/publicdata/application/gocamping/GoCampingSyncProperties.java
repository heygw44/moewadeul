package com.moewadeul.publicdata.application.gocamping;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("moewadeul.publicdata.gocamping.sync")
public record GoCampingSyncProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("0 0 3 * * *") String cron,
        @DefaultValue("30m") Duration staleAfter) {

    public GoCampingSyncProperties {
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("moewadeul.publicdata.gocamping.sync.cron must not be blank");
        }
        if (staleAfter.isZero() || staleAfter.isNegative()) {
            throw new IllegalArgumentException("moewadeul.publicdata.gocamping.sync.staleAfter must be positive");
        }
    }
}
