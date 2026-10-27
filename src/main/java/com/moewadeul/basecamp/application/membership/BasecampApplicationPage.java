package com.moewadeul.basecamp.application.membership;

import java.util.List;

public record BasecampApplicationPage(List<BasecampApplicationItem> content, int page, int size, boolean hasNext) {}
