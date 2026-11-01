package com.moewadeul.trust.infra.identity;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("moewadeul.identity")
public record IdentityProperties(String ciHmacKey) {

    static final int MIN_KEY_BYTES = 32;

    private static final String PREFIX = "moewadeul.identity.";

    public IdentityProperties {
        if (ciHmacKey == null || ciHmacKey.isBlank()) {
            throw new IllegalArgumentException(PREFIX + "ciHmacKey must not be blank (set CI_HMAC_KEY)");
        }
        if (ciHmacKey.getBytes(StandardCharsets.UTF_8).length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException(PREFIX + "ciHmacKey must be at least " + MIN_KEY_BYTES + " bytes");
        }
    }

    @Override
    public String toString() {
        return "IdentityProperties[ciHmacKey=****]";
    }
}
