package com.moewadeul.spot.application.detail;

import com.moewadeul.common.image.application.ImageView;
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
        List<ImageView> images,
        Instant createdAt) {}
