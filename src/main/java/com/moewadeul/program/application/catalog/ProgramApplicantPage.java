package com.moewadeul.program.application.catalog;

import java.util.List;

public record ProgramApplicantPage(List<ProgramApplicantItem> content, int page, int size, boolean hasNext) {}
