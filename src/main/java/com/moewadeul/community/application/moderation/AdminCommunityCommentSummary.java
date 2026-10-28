package com.moewadeul.community.application.moderation;

import java.time.Instant;
import java.util.List;

public record AdminCommunityCommentSummary(
        long commentId,
        long postId,
        String content,
        AdminCommunityAuthor author,
        String status,
        long reportCount,
        AdminCommunityReasonCounts reasonCounts,
        List<AdminCommunityRecentReport> recentReports,
        Instant statusChangedAt) {}
