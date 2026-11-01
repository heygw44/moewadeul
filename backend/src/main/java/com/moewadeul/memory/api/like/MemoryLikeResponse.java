package com.moewadeul.memory.api.like;

import com.moewadeul.memory.application.like.MemoryLikeResult;

public record MemoryLikeResponse(boolean liked, long likeCount) {

    static MemoryLikeResponse from(MemoryLikeResult result) {
        return new MemoryLikeResponse(result.liked(), result.likeCount());
    }
}
