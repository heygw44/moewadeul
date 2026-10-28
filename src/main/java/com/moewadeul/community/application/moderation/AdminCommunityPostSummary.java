package com.moewadeul.community.application.moderation;

import java.time.Instant;
import java.util.List;

public record AdminCommunityPostSummary(
        long postId,
        String title,
        String content,
        AdminCommunityAuthor author,
        String status,
        long reportCount,
        AdminCommunityReasonCounts reasonCounts,
        List<AdminCommunityRecentReport> recentReports,
        Instant statusChangedAt) {}
