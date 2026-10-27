package com.moewadeul.review.application;

import java.util.List;

public record SpotReviewReviseCommand(int rating, String content, List<Long> imageIds) {

    public SpotReviewReviseCommand(int rating, String content) {
        this(rating, content, null);
    }
}
