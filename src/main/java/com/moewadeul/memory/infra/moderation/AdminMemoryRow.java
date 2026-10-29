package com.moewadeul.memory.infra.moderation;

import com.moewadeul.memory.domain.MemoryStatus;
import java.time.Instant;
import java.time.LocalDate;

public record AdminMemoryRow(
        long memoryId,
        long authorId,
        String authorNickname,
        double latitude,
        double longitude,
        LocalDate visitedDate,
        int nights,
        String content,
        MemoryStatus status,
        long reportCount,
        Instant statusChangedAt) {}
