package com.moewadeul.memory.application;

import java.util.List;

/** 공개 피드의 한 쪽이다. 전체 개수는 없고, 다음 쪽이 있는지만 hasNext로 알린다. */
public record MemoryFeedPage(List<MemoryFeedItem> content, int page, int size, boolean hasNext) {}
