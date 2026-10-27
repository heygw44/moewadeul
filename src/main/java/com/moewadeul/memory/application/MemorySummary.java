package com.moewadeul.memory.application;

import java.time.Instant;
import java.time.LocalDate;

public record MemorySummary(
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
        String thumbnailUrl,
        long imageCount,
        long likeCount,
        Instant createdAt) {}
