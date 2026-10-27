package com.moewadeul.memory.application;

import com.moewadeul.memory.domain.ApproximateLocation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MemoryView(
        long memoryId,
        long authorId,
        String authorNickname,
        Long spotId,
        String spotName,
        Double latitude,
        Double longitude,
        ApproximateLocation approximate,
        LocalDate visitedDate,
        int nights,
        String content,
        String visibility,
        String status,
        List<MemoryPhoto> images,
        Instant createdAt,
        Instant updatedAt,
        long likeCount,
        Boolean likedByMe,
        boolean viewedByAuthor) {}
