package com.moewadeul.memory.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.memory.api.MemoryDetailResponse.LinkedSpot;
import com.moewadeul.memory.application.MemorySummary;
import com.moewadeul.memory.domain.MemoryStatus;
import com.moewadeul.memory.domain.MemoryVisibility;
import java.time.Instant;
import java.time.LocalDate;

public record MemorySummaryResponse(
        long memoryId,
        @JsonInclude(JsonInclude.Include.NON_NULL) LinkedSpot spot,
        double lat,
        double lng,
        LocalDate visitedDate,
        int nights,
        @JsonInclude(JsonInclude.Include.NON_NULL) String excerpt,
        MemoryVisibility visibility,
        MemoryStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL) String thumbnailUrl,
        long imageCount,
        Instant createdAt) {

    static MemorySummaryResponse from(MemorySummary summary) {
        return new MemorySummaryResponse(
                summary.memoryId(),
                LinkedSpot.of(summary.spotId(), summary.spotName()),
                summary.latitude(),
                summary.longitude(),
                summary.visitedDate(),
                summary.nights(),
                summary.excerpt(),
                summary.visibility(),
                summary.status(),
                summary.thumbnailUrl(),
                summary.imageCount(),
                summary.createdAt());
    }
}
