package com.moewadeul.trust.infra.report;

import com.moewadeul.trust.domain.report.ReportKind;
import com.moewadeul.trust.domain.report.ReportStatus;
import com.moewadeul.trust.domain.report.ReportType;
import java.time.Instant;
import java.time.LocalDate;

public record AdminMemberReportDetailRow(
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
        Instant createdAt,
        String content,
        String resultNote,
        Long handledBy,
        Instant handledAt,
        String basecampTitle,
        String basecampStatus,
        LocalDate basecampStartDate,
        Long reviewId,
        String reviewComment,
        Boolean reviewRejoinWanted,
        Boolean reviewHidden) {}
