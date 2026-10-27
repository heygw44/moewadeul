package com.moewadeul.trust.infra.companion;

import java.time.Instant;

public record CompanionReviewWriteContextRow(
        String basecampStatus,
        Instant completedAt,
        boolean reviewerActive,
        boolean revieweeActive,
        boolean alreadyWritten) {}
