package com.moewadeul.review.infra;

import java.time.Instant;
import java.time.LocalDate;

public record SpotReviewRow(
        long reviewId,
        long authorId,
        String authorNickname,
        LocalDate visitedDate,
        int rating,
        String content,
        Instant createdAt) {}
