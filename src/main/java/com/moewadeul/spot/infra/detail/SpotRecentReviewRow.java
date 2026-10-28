package com.moewadeul.spot.infra.detail;

import java.time.Instant;
import java.time.LocalDate;

public record SpotRecentReviewRow(
        long reviewId,
        long authorId,
        String authorNickname,
        LocalDate visitedDate,
        int rating,
        String content,
        Instant createdAt) {}
