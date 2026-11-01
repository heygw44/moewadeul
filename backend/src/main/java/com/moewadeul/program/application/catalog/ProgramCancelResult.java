package com.moewadeul.program.application.catalog;

public record ProgramCancelResult(long programId, String status, int canceledApplicationCount) {}
