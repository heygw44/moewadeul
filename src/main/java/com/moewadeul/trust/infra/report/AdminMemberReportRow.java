package com.moewadeul.trust.infra.report;

import com.moewadeul.trust.domain.report.ReportKind;
import com.moewadeul.trust.domain.report.ReportStatus;
import com.moewadeul.trust.domain.report.ReportType;
import java.time.Instant;

public record AdminMemberReportRow(
        long reportId,
        ReportKind kind,
        ReportType type,
        boolean urgent,
        ReportStatus status,
        long reporterId,
        String reporterNickname,
        long targetId,
        String targetNickname,
        long basecampId,
        Instant createdAt) {}
