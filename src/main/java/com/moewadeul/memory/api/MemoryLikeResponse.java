package com.moewadeul.memory.api;

import com.moewadeul.memory.application.MemoryLikeResult;

public record MemoryLikeResponse(boolean liked, long likeCount) {

    static MemoryLikeResponse from(MemoryLikeResult result) {
        return new MemoryLikeResponse(result.liked(), result.likeCount());
    }
}
