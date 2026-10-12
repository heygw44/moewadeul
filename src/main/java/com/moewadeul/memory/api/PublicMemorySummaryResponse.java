package com.moewadeul.memory.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.memory.api.MemoryDetailResponse.LinkedSpot;
import com.moewadeul.memory.application.PublicMemorySummary;
import java.time.Instant;
import java.time.LocalDate;

// 다른 회원의 추억지도에 보이는 공개 기록이라 정확한 좌표(lat, lng)와 공개 범위는 담지 않고 대략 좌표만 담는다.
// 연결한 장소가 보이지 않으면 spot을, 글이 없으면 excerpt를, 사진이 없으면 thumbnailUrl을 null이 아니라 필드째 뺀다.
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
