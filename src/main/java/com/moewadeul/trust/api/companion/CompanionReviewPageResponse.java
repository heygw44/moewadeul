package com.moewadeul.trust.api.companion;

import com.moewadeul.trust.application.companion.CompanionReviewPage;
import java.util.List;
import java.util.function.Function;

public record CompanionReviewPageResponse<T>(List<T> content, int page, int size, boolean hasNext) {

    static <S, T> CompanionReviewPageResponse<T> from(CompanionReviewPage<S> page, Function<S, T> mapper) {
        return new CompanionReviewPageResponse<>(
                page.content().stream().map(mapper).toList(), page.page(), page.size(), page.hasNext());
    }
}
