package com.moewadeul.program.application.enrollment;

import java.time.Instant;

public record ProgramPayResult(long applicationId, String status, Instant paidAt) {}
