package com.moewadeul.trust.domain.sanction;

public enum SanctionType {
    WARNING,
    SUSPEND_7D,
    SUSPEND_30D,
    PERMANENT,
    TEMPORARY_72H;

    public boolean isSuspension() {
        return this != WARNING;
    }
}
