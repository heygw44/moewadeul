package com.moewadeul.memory.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.memory.api.MemoryDetailResponse.LinkedSpot;
import com.moewadeul.memory.application.MemorySummary;
import java.time.Instant;
import java.time.LocalDate;

// 작성자 본인에게만 주는 내 기록 목록의 항목이라 정확한 좌표와 공개 범위를 담는다.
// 연결한 장소가 보이지 않으면 spot을, 글이 없으면 excerpt를, 사진이 없으면 thumbnailUrl을 null이 아니라 필드째 뺀다.
public record MemorySummaryResponse(
        long memoryId,
        @JsonInclude(JsonInclude.Include.NON_NULL) LinkedSpot spot,
        double lat,
        double lng,
        LocalDate visitedDate,
        int nights,
        @JsonInclude(JsonInclude.Include.NON_NULL) String excerpt,
        String visibility,
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
                summary.thumbnailUrl(),
                summary.imageCount(),
                summary.createdAt());
    }
}
