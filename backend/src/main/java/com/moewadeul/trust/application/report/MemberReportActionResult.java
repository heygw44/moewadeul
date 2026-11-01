package com.moewadeul.trust.application.report;

public record MemberReportActionResult(
        long reportId, String status, Long sanctionId, long targetMemberId, boolean suspended) {}
