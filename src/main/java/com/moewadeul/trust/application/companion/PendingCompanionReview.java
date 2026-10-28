package com.moewadeul.trust.application.companion;

import java.time.Instant;
import java.util.List;

public record PendingCompanionReview(
        long basecampId, String basecampTitle, Instant completedAt, Instant deadline, List<Target> targets) {

    public record Target(long memberId, String nickname) {}
}
