package com.moewadeul.memory.infra;

import com.moewadeul.memory.domain.MemoryStatus;
import com.moewadeul.memory.domain.MemoryVisibility;
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
        MemoryVisibility visibility,
        MemoryStatus status,
        String thumbnailKey,
        long imageCount,
        long likeCount,
        Instant createdAt) {}
