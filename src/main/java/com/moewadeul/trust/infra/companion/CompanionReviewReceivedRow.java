package com.moewadeul.trust.infra.companion;

import java.time.Instant;

public record CompanionReviewReceivedRow(
        long reviewId,
        long basecampId,
        String basecampTitle,
        Instant completedAt,
        long reviewerId,
        String reviewerNickname,
        boolean rejoinWanted,
        String tags,
        String comment,
        Instant createdAt,
        boolean reverseWritten) {}
