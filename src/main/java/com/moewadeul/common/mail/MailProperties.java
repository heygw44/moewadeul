package com.moewadeul.common.mail;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("moewadeul.mail")
public record MailProperties(String from, String fromName, Duration sendTimeout) {

    public MailProperties {
        if (sendTimeout == null || sendTimeout.isZero() || sendTimeout.isNegative()) {
            throw new IllegalArgumentException("moewadeul.mail.sendTimeout must be positive");
        }
    }
}
