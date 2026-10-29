package com.moewadeul.memory.infra.moderation;

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
        String status,
        long reportCount,
        Instant statusChangedAt) {}
