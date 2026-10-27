package com.moewadeul.memory.application;

import com.moewadeul.memory.domain.ApproximateLocation;
import java.time.Instant;
import java.time.LocalDate;

public record MemoryFeedItem(
        long memoryId,
        long authorId,
        String authorNickname,
        Long spotId,
        String spotName,
        ApproximateLocation approximate,
        LocalDate visitedDate,
        int nights,
        String excerpt,
        String thumbnailUrl,
        long imageCount,
        long likeCount,
        Boolean likedByMe,
        Instant createdAt) {}
