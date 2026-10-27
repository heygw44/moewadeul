package com.moewadeul.review.application;

import com.moewadeul.review.infra.SpotReviewRow;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SpotReviewItem(
        long reviewId,
        long authorId,
        String authorNickname,
        LocalDate visitedDate,
        int rating,
        String content,
        List<SpotReviewImage> images,
        Instant createdAt) {

    static SpotReviewItem from(SpotReviewRow row, List<SpotReviewImage> images) {
        return new SpotReviewItem(
                row.reviewId(),
                row.authorId(),
                row.authorNickname(),
                row.visitedDate(),
                row.rating(),
                row.content(),
                List.copyOf(images),
                row.createdAt());
    }
}
