package com.moewadeul.trust.application.report;

public record MemberReportActionCommand(
        long reportId, long adminId, String sanctionType, String sanctionReason, boolean hideReview, String note) {}
