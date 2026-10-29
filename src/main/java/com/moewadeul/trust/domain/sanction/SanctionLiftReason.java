package com.moewadeul.trust.domain.sanction;

public enum SanctionLiftReason {
    END_EARLY(SanctionStatus.ENDED_EARLY),
    REVOKE(SanctionStatus.REVOKED);

    private final SanctionStatus resultingStatus;

    SanctionLiftReason(SanctionStatus resultingStatus) {
        this.resultingStatus = resultingStatus;
    }

    public SanctionStatus resultingStatus() {
        return resultingStatus;
    }
}
