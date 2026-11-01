package com.moewadeul.member.application.password;

public final class PasswordResetEvents {

    public static final String EVENT_TYPE = "PASSWORD_RESET_REQUESTED";
    public static final String AGGREGATE_TYPE = "MEMBER";

    private PasswordResetEvents() {}

    public record Payload() {}
}
