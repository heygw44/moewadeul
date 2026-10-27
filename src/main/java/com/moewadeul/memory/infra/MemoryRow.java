package com.moewadeul.memory.infra;

import java.time.Instant;
import java.time.LocalDate;

public record MemoryRow(
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
        String status,
        Instant createdAt,
        Instant updatedAt,
        long likeCount,
        Boolean likedByMe) {}
