package com.moewadeul.basecamp.infra.membership;

import com.moewadeul.basecamp.domain.BasecampApplicationStatus;
import java.time.Instant;

public record BasecampApplicationRow(
        long applicationId, long applicantId, BasecampApplicationStatus status, String message, Instant appliedAt) {}
