package com.moewadeul.member.application;

public final class EmailVerificationEvents {

    public static final String EVENT_TYPE = "EMAIL_VERIFICATION_REQUESTED";
    public static final String AGGREGATE_TYPE = "MEMBER";

    private EmailVerificationEvents() {}

    public record Payload(String requestIp) {

        @Override
        public String toString() {
            return "Payload[****]";
        }
    }
}
