package com.moewadeul.program.application.enrollment;

import com.moewadeul.program.domain.ProgramApplicationStatus;
import java.time.Instant;

public record ProgramPayResult(long applicationId, ProgramApplicationStatus status, Instant paidAt) {}
