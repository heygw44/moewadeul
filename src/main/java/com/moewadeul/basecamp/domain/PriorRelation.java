package com.moewadeul.basecamp.domain;

public enum PriorRelation {
    NONE,
    ACTIVE,
    BLOCKED;

    public static PriorRelation of(BasecampMemberStatus memberStatus, BasecampApplicationStatus applicationStatus) {
        if (memberStatus == BasecampMemberStatus.ACTIVE) {
            return ACTIVE;
        }
        if (memberStatus == BasecampMemberStatus.LEFT || memberStatus == BasecampMemberStatus.KICKED) {
            return BLOCKED;
        }
        if (applicationStatus == BasecampApplicationStatus.PENDING) {
            return ACTIVE;
        }
        if (applicationStatus == BasecampApplicationStatus.REJECTED) {
            return BLOCKED;
        }
        return NONE;
    }
}
