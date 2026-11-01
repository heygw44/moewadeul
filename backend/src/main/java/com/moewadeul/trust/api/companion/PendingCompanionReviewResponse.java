package com.moewadeul.trust.api.companion;

import com.moewadeul.trust.application.companion.PendingCompanionReview;
import java.time.Instant;
import java.util.List;

public record PendingCompanionReviewResponse(
        long basecampId, String basecampTitle, Instant completedAt, Instant deadline, List<TargetResponse> targets) {

    static PendingCompanionReviewResponse from(PendingCompanionReview pending) {
        List<TargetResponse> targets = pending.targets().stream()
                .map(target -> new TargetResponse(target.memberId(), target.nickname()))
                .toList();
        return new PendingCompanionReviewResponse(
                pending.basecampId(), pending.basecampTitle(), pending.completedAt(), pending.deadline(), targets);
    }

    public record TargetResponse(long memberId, String nickname) {}
}
