package com.moewadeul.basecamp.application.query;

import java.util.List;

public record BasecampSearchPage(List<BasecampSearchItem> content, int page, int size, boolean hasNext) {}
