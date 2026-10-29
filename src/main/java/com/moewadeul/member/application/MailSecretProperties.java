package com.moewadeul.member.application;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("moewadeul.member.mail-secret")
public record MailSecretProperties(String hmacKey) {

    static final int MIN_KEY_BYTES = 32;

    private static final String PREFIX = "moewadeul.member.mail-secret.";

    public MailSecretProperties {
        if (hmacKey == null || hmacKey.isBlank()) {
            throw new IllegalArgumentException(PREFIX + "hmacKey must not be blank (set MAIL_SECRET_HMAC_KEY)");
        }
        if (hmacKey.getBytes(StandardCharsets.UTF_8).length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException(PREFIX + "hmacKey must be at least " + MIN_KEY_BYTES + " bytes");
        }
    }

    @Override
    public String toString() {
        return "MailSecretProperties[hmacKey=****]";
    }
}
