package com.moewadeul.trust.domain.report;

public enum ReportType {
    NO_SHOW(ReportKind.MEMBER, false),
    MONEY_REQUEST(ReportKind.MEMBER, false),
    HARASSMENT_OR_THREAT(ReportKind.MEMBER, true),
    OFFENSIVE_BEHAVIOR(ReportKind.MEMBER, false),
    FAKE_PROFILE(ReportKind.MEMBER, false),
    ILLEGAL_CAMPING_INDUCEMENT(ReportKind.MEMBER, false),
    INAPPROPRIATE_REVIEW(ReportKind.REVIEW, false);

    private final ReportKind kind;
    private final boolean urgent;

    ReportType(ReportKind kind, boolean urgent) {
        this.kind = kind;
        this.urgent = urgent;
    }

    public ReportKind kind() {
        return kind;
    }

    public boolean isUrgent() {
        return urgent;
    }
}
