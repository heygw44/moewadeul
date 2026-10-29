package com.moewadeul.memory.application.moderation;

import com.moewadeul.common.moderation.RecentReport;
import com.moewadeul.memory.application.MemoryPhoto;
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
        List<MemoryPhoto> images,
        String status,
        long reportCount,
        Map<String, Long> reasonCounts,
        List<RecentReport> recentReports,
        Instant statusChangedAt) {}
