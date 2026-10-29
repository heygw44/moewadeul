package com.moewadeul.community.application.moderation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AdminCommunityPostSummary(
        long postId,
        String title,
        String content,
        AdminCommunityAuthor author,
        String status,
        long reportCount,
        Map<String, Long> reasonCounts,
        List<AdminCommunityRecentReport> recentReports,
        Instant statusChangedAt) {}
