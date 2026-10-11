package com.moewadeul.review.api;

import com.moewadeul.review.application.SpotReviewPage;
import java.util.List;

public record SpotReviewPageResponse(List<SpotReviewItemResponse> content, int page, int size, boolean hasNext) {

    static SpotReviewPageResponse from(SpotReviewPage page) {
        List<SpotReviewItemResponse> content =
                page.content().stream().map(SpotReviewItemResponse::from).toList();
        return new SpotReviewPageResponse(content, page.page(), page.size(), page.hasNext());
    }
}
