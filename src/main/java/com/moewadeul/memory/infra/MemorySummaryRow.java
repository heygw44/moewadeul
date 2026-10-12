package com.moewadeul.memory.infra;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 내 기록 목록의 한 행. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다.
 *
 * <p>spotId와 spotName은 연결한 장소가 없거나 ACTIVE가 아니면 null이다. excerpt는 글 앞 100자이고 글이 없으면 null이다.
 * thumbnailKey는 기록 안 순서가 가장 앞선 사진의 저장소 객체 키로, 사진이 없으면 null이고 응답에는 내보내지 않는다.
 */
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
        String thumbnailKey,
        long imageCount,
        long likeCount,
        Instant createdAt) {}
