package com.moewadeul.memory.application;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 내 기록 목록의 한 항목. 작성자 본인에게만 주므로 정확한 좌표와 공개 범위를 담는다.
 *
 * @param spotId 연결한 장소가 ACTIVE일 때만 값이 있다. 아니면 spotName과 함께 null이다
 * @param excerpt 글 앞 100자. 글이 없으면 null이다
 * @param thumbnailUrl 첫 사진의 조회 URL. 사진이 없으면 null이다
 */
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
        String thumbnailUrl,
        long imageCount,
        long likeCount,
        Instant createdAt) {}
