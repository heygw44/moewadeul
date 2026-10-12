package com.moewadeul.admin.api;

import com.moewadeul.memory.application.MemoryModerationResult;

/** 추억 기록 숨김·복구의 응답. status는 처리 뒤의 기록 상태다. */
public record MemoryStatusResponse(long memoryId, String status) {

    static MemoryStatusResponse from(MemoryModerationResult result) {
        return new MemoryStatusResponse(result.memoryId(), result.status());
    }
}
