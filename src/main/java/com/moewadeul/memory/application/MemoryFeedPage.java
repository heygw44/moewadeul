package com.moewadeul.memory.application;

import java.util.List;

public record MemoryFeedPage(List<MemoryFeedItem> content, int page, int size, boolean hasNext) {}
