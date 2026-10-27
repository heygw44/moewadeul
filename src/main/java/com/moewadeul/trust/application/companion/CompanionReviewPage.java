package com.moewadeul.trust.application.companion;

import java.util.List;

public record CompanionReviewPage<T>(List<T> content, int page, int size, boolean hasNext) {}
