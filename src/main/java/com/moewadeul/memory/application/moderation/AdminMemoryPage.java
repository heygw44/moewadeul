package com.moewadeul.memory.application.moderation;

import java.util.List;

public record AdminMemoryPage(List<AdminMemorySummary> content, int page, int size, boolean hasNext) {}
