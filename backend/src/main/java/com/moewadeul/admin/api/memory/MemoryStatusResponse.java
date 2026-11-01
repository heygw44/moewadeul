package com.moewadeul.admin.api.memory;

import com.moewadeul.common.moderation.ModerationResult;

public record MemoryStatusResponse(long memoryId, String status) {

    static MemoryStatusResponse from(ModerationResult result) {
        return new MemoryStatusResponse(result.targetId(), result.status());
    }
}
