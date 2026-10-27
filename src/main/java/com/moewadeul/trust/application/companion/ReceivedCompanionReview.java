package com.moewadeul.trust.application.companion;

import com.moewadeul.trust.domain.companion.CompanionReviewTag;
import java.time.Instant;
import java.util.List;

public record ReceivedCompanionReview(
        long basecampId,
        boolean revealed,
        Long reviewId,
        String basecampTitle,
        Long reviewerId,
        String reviewerNickname,
        Boolean rejoinWanted,
        List<CompanionReviewTag> tags,
        String comment,
        Instant createdAt) {

    static ReceivedCompanionReview sealed(long basecampId) {
        return new ReceivedCompanionReview(basecampId, false, null, null, null, null, null, null, null, null);
    }
}
