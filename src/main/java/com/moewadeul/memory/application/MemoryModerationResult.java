package com.moewadeul.memory.application;

/** 추억 기록을 숨기거나 복구한 결과. status는 처리 뒤의 상태 이름이다. */
public record MemoryModerationResult(long memoryId, String status) {}
