package com.moewadeul.memory.application.moderation;

import com.moewadeul.common.image.application.ImageView;
import com.moewadeul.common.moderation.RecentReport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record AdminMemorySummary(
        long memoryId,
        long authorId,
        String authorNickname,
        double approxLat,
        double approxLng,
        LocalDate visitedDate,
        int nights,
        String content,
        List<ImageView> images,
        String status,
        long reportCount,
        Map<String, Long> reasonCounts,
        List<RecentReport> recentReports,
        Instant statusChangedAt) {}
