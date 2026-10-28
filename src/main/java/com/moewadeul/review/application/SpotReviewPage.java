package com.moewadeul.review.application;

import java.util.List;

public record SpotReviewPage(List<SpotReviewItem> content, int page, int size, boolean hasNext) {}
