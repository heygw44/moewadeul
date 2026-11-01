package com.moewadeul.program.application.catalog;

import com.moewadeul.program.domain.ProgramPhase;

public record ProgramListQuery(ProgramPhase phase, int page, int size) {}
