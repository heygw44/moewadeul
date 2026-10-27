package com.moewadeul.trust.api.companion;

import com.moewadeul.trust.application.companion.ReceivedCompanionReview;
import com.moewadeul.trust.domain.companion.CompanionReviewTag;
import java.time.Instant;
import java.util.List;

public sealed interface ReceivedCompanionReviewResponse {

    record Revealed(
            long reviewId,
            long basecampId,
            String basecampTitle,
            ReviewerResponse reviewer,
            boolean rejoinWanted,
            List<CompanionReviewTag> tags,
            String comment,
            Instant createdAt,
            boolean revealed)
            implements ReceivedCompanionReviewResponse {}

    record Sealed(long basecampId, boolean revealed) implements ReceivedCompanionReviewResponse {}

    record ReviewerResponse(long memberId, String nickname) {}

    static ReceivedCompanionReviewResponse from(ReceivedCompanionReview review) {
        if (!review.revealed()) {
            return new Sealed(review.basecampId(), false);
        }
        return new Revealed(
                review.reviewId(),
                review.basecampId(),
                review.basecampTitle(),
                new ReviewerResponse(review.reviewerId(), review.reviewerNickname()),
                review.rejoinWanted(),
                review.tags(),
                review.comment(),
                review.createdAt(),
                true);
    }
}
