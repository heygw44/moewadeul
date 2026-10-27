package com.moewadeul.trust.infra.report;

import java.time.Instant;

public record AdminMemberReportRow(
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
