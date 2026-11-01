package com.moewadeul.program.application.enrollment;

import com.moewadeul.program.domain.ProgramApplicationStatus;
import java.time.Instant;

public record ProgramApplyResult(
        long applicationId, ProgramApplicationStatus status, Instant paymentDueAt, int amount) {}
