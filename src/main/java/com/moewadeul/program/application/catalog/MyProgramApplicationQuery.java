package com.moewadeul.program.application.catalog;

import com.moewadeul.program.domain.ProgramApplicationStatus;

public record MyProgramApplicationQuery(ProgramApplicationStatus status, int page, int size) {}
