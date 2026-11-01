package com.moewadeul.review.application;

import java.time.LocalDate;
import java.util.List;

public record SpotReviewWriteCommand(LocalDate visitedDate, int rating, String content, List<Long> imageIds) {

    public SpotReviewWriteCommand {
        imageIds = imageIds == null ? List.of() : imageIds;
    }

    public SpotReviewWriteCommand(LocalDate visitedDate, int rating, String content) {
        this(visitedDate, rating, content, List.of());
    }
}
