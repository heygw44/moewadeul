package com.moewadeul.basecamp.domain;

public enum CancelReason {
    LEADER(false),
    NOT_ENOUGH_MEMBERS(true),
    LEADER_SANCTIONED(false),
    LEADER_WITHDRAWN(false);

    private final boolean notifiesLeader;

    CancelReason(boolean notifiesLeader) {
        this.notifiesLeader = notifiesLeader;
    }

    boolean notifiesLeader() {
        return notifiesLeader;
    }
}
