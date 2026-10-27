package com.moewadeul.spot.application.detail;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SpotRecentReview(
        long reviewId,
        long authorId,
        String authorNickname,
        LocalDate visitedDate,
        int rating,
        String content,
        List<Image> images,
        Instant createdAt) {

    public record Image(long imageId, String url) {}
}
