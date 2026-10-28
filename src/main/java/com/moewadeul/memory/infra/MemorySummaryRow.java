package com.moewadeul.memory.infra;

import java.time.Instant;
import java.time.LocalDate;

public record MemorySummaryRow(
        long memoryId,
        Long spotId,
        String spotName,
        double latitude,
        double longitude,
        LocalDate visitedDate,
        int nights,
        String excerpt,
        String visibility,
        String status,
        String thumbnailKey,
        long imageCount,
        long likeCount,
        Instant createdAt) {}
