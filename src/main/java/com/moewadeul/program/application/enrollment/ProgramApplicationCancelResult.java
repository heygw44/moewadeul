package com.moewadeul.program.application.enrollment;

import com.moewadeul.program.domain.ProgramApplicationStatus;

public record ProgramApplicationCancelResult(ProgramApplicationStatus status, boolean refunded) {}
