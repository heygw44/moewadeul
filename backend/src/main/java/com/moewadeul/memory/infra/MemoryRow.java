package com.moewadeul.memory.infra;

import com.moewadeul.memory.domain.MemoryStatus;
import com.moewadeul.memory.domain.MemoryVisibility;
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
        MemoryVisibility visibility,
        MemoryStatus status,
        Instant createdAt,
        Instant updatedAt,
        long likeCount,
        Boolean likedByMe) {}
