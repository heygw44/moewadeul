package com.moewadeul.admin.api.community;

import com.moewadeul.common.moderation.ModerationResult;

public record CommunityCommentStatusResponse(long commentId, String status) {

    static CommunityCommentStatusResponse from(ModerationResult result) {
        return new CommunityCommentStatusResponse(result.targetId(), result.status());
    }
}
