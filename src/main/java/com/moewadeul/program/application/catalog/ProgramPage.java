package com.moewadeul.program.application.catalog;

import java.time.Instant;
import java.util.List;

public record ProgramPage(List<ProgramSummary> content, int page, int size, boolean hasNext, Instant nextApplyOpenAt) {}
