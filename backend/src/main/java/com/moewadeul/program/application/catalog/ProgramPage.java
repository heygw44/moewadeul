package com.moewadeul.program.application.catalog;

import com.moewadeul.common.paging.PageSlice;
import java.time.Instant;

public record ProgramPage(PageSlice<ProgramSummary> slice, Instant nextApplyOpenAt) {}
