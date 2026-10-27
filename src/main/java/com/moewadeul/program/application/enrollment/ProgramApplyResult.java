package com.moewadeul.program.application.enrollment;

import java.time.Instant;

public record ProgramApplyResult(long applicationId, String status, Instant paymentDueAt, int amount) {}
