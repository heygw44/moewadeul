package com.moewadeul.trust.application.report;

import java.time.Instant;

public record AdminMemberReportSummary(
        long reportId,
        String kind,
        String type,
        boolean urgent,
        String status,
        long reporterId,
        String reporterNickname,
        long targetId,
        String targetNickname,
        long basecampId,
        Instant createdAt) {}
