package com.moewadeul.admin.api.memory;

import com.moewadeul.memory.application.moderation.MemoryModerationResult;

public record MemoryStatusResponse(long memoryId, String status) {

    static MemoryStatusResponse from(MemoryModerationResult result) {
        return new MemoryStatusResponse(result.memoryId(), result.status());
    }
}
