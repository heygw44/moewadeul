package com.moewadeul.member.domain;

import java.time.Instant;
import java.util.Map;

public class MemberSuspendedException extends MemberException {

    private static final String SUSPENDED_UNTIL_FIELD = "suspendedUntil";

    private final Instant suspendedUntil;

    public MemberSuspendedException(Instant suspendedUntil) {
        super(MemberErrorCode.MEMBER_SUSPENDED);
        this.suspendedUntil = suspendedUntil;
    }

    @Override
    public Map<String, Object> extraFields() {
        if (suspendedUntil == null) {
            return Map.of();
        }
        return Map.of(SUSPENDED_UNTIL_FIELD, suspendedUntil);
    }
}
