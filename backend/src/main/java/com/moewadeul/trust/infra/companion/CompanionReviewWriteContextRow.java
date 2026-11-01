package com.moewadeul.trust.infra.companion;

import java.time.Instant;

public record CompanionReviewWriteContextRow(
        boolean basecampCompleted,
        Instant completedAt,
        boolean reviewerActive,
        boolean revieweeActive,
        boolean alreadyWritten) {}
