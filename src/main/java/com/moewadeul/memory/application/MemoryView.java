package com.moewadeul.memory.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 기록 한 건을 보여 줄 때 필요한 값. 공개 범위에 맞춰 필드를 빼는 일은 api 계층이 viewedByAuthor를 보고 한다.
 *
 * @param spotId 연결한 장소가 ACTIVE일 때만 값이 있다. 아니면 spotName과 함께 null이다
 * @param content 글이 없으면 null이다
 * @param viewedByAuthor 조회한 사람이 작성자이면 true다. 정확한 좌표는 이때만 내보낸다
 */
public record MemoryView(
        long memoryId,
        long authorId,
        String authorNickname,
        Long spotId,
        String spotName,
        double latitude,
        double longitude,
        LocalDate visitedDate,
        int nights,
        String content,
        String visibility,
        List<MemoryPhoto> images,
        Instant createdAt,
        Instant updatedAt,
        boolean viewedByAuthor) {}
