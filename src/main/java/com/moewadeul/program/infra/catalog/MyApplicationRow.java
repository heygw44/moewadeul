package com.moewadeul.program.infra.catalog;

import com.moewadeul.program.domain.ProgramApplicationStatus;
import java.time.Instant;

public record MyApplicationRow(long applicationId, ProgramApplicationStatus status, Instant paymentDueAt) {}
