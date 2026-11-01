package com.moewadeul.memory.infra;

import java.time.Instant;
import java.time.LocalDate;

public record MemoryFeedRow(
        long memoryId,
        long authorId,
        String authorNickname,
        Long spotId,
        String spotName,
        double latitude,
        double longitude,
        LocalDate visitedDate,
        int nights,
        String excerpt,
        String thumbnailKey,
        long imageCount,
        long likeCount,
        Boolean likedByMe,
        Instant createdAt) {}
