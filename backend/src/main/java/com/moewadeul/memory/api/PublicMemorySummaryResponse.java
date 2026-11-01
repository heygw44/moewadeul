package com.moewadeul.memory.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.memory.api.MemoryDetailResponse.LinkedSpot;
import com.moewadeul.memory.application.PublicMemorySummary;
import java.time.Instant;
import java.time.LocalDate;

public record PublicMemorySummaryResponse(
        long memoryId,
        @JsonInclude(JsonInclude.Include.NON_NULL) LinkedSpot spot,
        double approxLat,
        double approxLng,
        LocalDate visitedDate,
        int nights,
        @JsonInclude(JsonInclude.Include.NON_NULL) String excerpt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String thumbnailUrl,
        long imageCount,
        long likeCount,
        Instant createdAt) {

    static PublicMemorySummaryResponse from(PublicMemorySummary summary) {
        return new PublicMemorySummaryResponse(
                summary.memoryId(),
                LinkedSpot.of(summary.spotId(), summary.spotName()),
                summary.approximate().latitude(),
                summary.approximate().longitude(),
                summary.visitedDate(),
                summary.nights(),
                summary.excerpt(),
                summary.thumbnailUrl(),
                summary.imageCount(),
                summary.likeCount(),
                summary.createdAt());
    }
}
