package com.moewadeul.common.mail;

import java.util.Objects;

public record MailMessage(String to, String subject, String body) {

    public MailMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(body, "body");
    }

    @Override
    public String toString() {
        return "MailMessage[REDACTED]";
    }
}
