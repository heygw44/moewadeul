package com.moewadeul.memory.application;

import com.moewadeul.memory.domain.ApproximateLocation;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 다른 회원의 추억지도에 보이는 공개 기록 한 건. 정확한 좌표와 공개 범위는 담지 않는다.
 *
 * @param spotId 연결한 장소가 ACTIVE일 때만 값이 있다. 아니면 spotName과 함께 null이다
 * @param excerpt 글 앞 100자. 글이 없으면 null이다
 * @param thumbnailUrl 첫 사진의 조회 URL. 사진이 없으면 null이다
 */
public record PublicMemorySummary(
        long memoryId,
        Long spotId,
        String spotName,
        ApproximateLocation approximate,
        LocalDate visitedDate,
        int nights,
        String excerpt,
        String thumbnailUrl,
        long imageCount,
        long likeCount,
        Instant createdAt) {}
