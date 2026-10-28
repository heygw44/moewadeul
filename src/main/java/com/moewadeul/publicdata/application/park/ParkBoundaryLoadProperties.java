package com.moewadeul.publicdata.application.park;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("moewadeul.publicdata.park-boundary")
public record ParkBoundaryLoadProperties(
        Path file, @DefaultValue("30m") Duration staleAfter) {

    static final String FILE_PROPERTY = "moewadeul.publicdata.park-boundary.file";

    public ParkBoundaryLoadProperties {
        if (staleAfter.isZero() || staleAfter.isNegative()) {
            throw new IllegalArgumentException("moewadeul.publicdata.park-boundary.staleAfter must be positive");
        }
    }
}
