package com.moewadeul.publicdata.application.forest;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("moewadeul.publicdata.forest")
public record ForestLoadProperties(
        Path file, @DefaultValue("30m") Duration staleAfter) {

    static final String FILE_PROPERTY = "moewadeul.publicdata.forest.file";

    public ForestLoadProperties {
        if (staleAfter.isZero() || staleAfter.isNegative()) {
            throw new IllegalArgumentException("moewadeul.publicdata.forest.staleAfter must be positive");
        }
    }
}
