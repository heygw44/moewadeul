package com.moewadeul.trust.infra.report;

import java.time.Instant;
import java.time.LocalDate;

public record AdminMemberReportDetailRow(
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
