package com.moewadeul.trust.api.companion;

import com.moewadeul.trust.application.companion.PublicCompanionReview;
import com.moewadeul.trust.domain.companion.CompanionReviewTag;
import java.time.Instant;
import java.util.List;

public record PublicCompanionReviewResponse(List<CompanionReviewTag> tags, String comment, Instant createdAt) {

    static PublicCompanionReviewResponse from(PublicCompanionReview review) {
        return new PublicCompanionReviewResponse(review.tags(), review.comment(), review.createdAt());
    }
}
