package com.moewadeul.memory.application;

import com.moewadeul.memory.domain.MemoryStatus;
import com.moewadeul.memory.domain.MemoryVisibility;
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
        MemoryVisibility visibility,
        MemoryStatus status,
        String thumbnailUrl,
        long imageCount,
        long likeCount,
        Instant createdAt) {}
