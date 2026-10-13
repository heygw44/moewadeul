package com.moewadeul.memory.application;

import java.util.List;

/** 관리자 기록 검토 목록의 한 쪽이다. 전체 개수는 없고, 다음 쪽이 있는지만 hasNext로 알린다. */
public record AdminMemoryPage(List<AdminMemorySummary> content, int page, int size, boolean hasNext) {}
