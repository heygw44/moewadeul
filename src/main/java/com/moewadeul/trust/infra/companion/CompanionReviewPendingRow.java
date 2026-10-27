package com.moewadeul.trust.infra.companion;

import java.time.Instant;

public record CompanionReviewPendingRow(
        long basecampId, String basecampTitle, Instant completedAt, long targetId, String targetNickname) {}
