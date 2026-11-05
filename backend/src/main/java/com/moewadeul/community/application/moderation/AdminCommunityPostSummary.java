package com.moewadeul.community.application.moderation;

import com.moewadeul.common.image.application.ImageView;
import com.moewadeul.common.moderation.RecentReport;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AdminCommunityPostSummary(
        long postId,
        String title,
        String content,
        AdminCommunityAuthor author,
        List<ImageView> images,
        String status,
        long reportCount,
        Map<String, Long> reasonCounts,
        List<RecentReport> recentReports,
        Instant statusChangedAt) {}
