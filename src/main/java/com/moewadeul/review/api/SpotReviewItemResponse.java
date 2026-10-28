package com.moewadeul.review.api;

import com.moewadeul.review.application.SpotReviewItem;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SpotReviewItemResponse(
        long reviewId,
        Author author,
        LocalDate visitedDate,
        int rating,
        String content,
        List<Image> images,
        Instant createdAt) {

    static SpotReviewItemResponse from(SpotReviewItem item) {
        return new SpotReviewItemResponse(
                item.reviewId(),
                new Author(item.authorId(), item.authorNickname()),
                item.visitedDate(),
                item.rating(),
                item.content(),
                item.images().stream()
                        .map(image -> new Image(image.imageId(), image.url()))
                        .toList(),
                item.createdAt());
    }

    public record Author(long memberId, String nickname) {}

    public record Image(long imageId, String url) {}
}
