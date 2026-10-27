package com.moewadeul.memory.application.moderation;

import com.moewadeul.memory.application.MemoryPhoto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
        ReasonCounts reasonCounts,
        List<RecentReport> recentReports,
        Instant statusChangedAt) {

    public record ReasonCounts(long spam, long abuse, long illegalCamping, long privacy, long moneyScam, long other) {}

    public record RecentReport(String reason, String content, Instant createdAt) {}
}
