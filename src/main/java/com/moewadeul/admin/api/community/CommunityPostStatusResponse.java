package com.moewadeul.admin.api.community;

import com.moewadeul.common.moderation.ModerationResult;

public record CommunityPostStatusResponse(long postId, String status) {

    static CommunityPostStatusResponse from(ModerationResult result) {
        return new CommunityPostStatusResponse(result.targetId(), result.status());
    }
}
