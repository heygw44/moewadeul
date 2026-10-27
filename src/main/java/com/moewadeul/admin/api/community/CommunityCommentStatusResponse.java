package com.moewadeul.admin.api.community;

import com.moewadeul.community.application.moderation.CommunityModerationResult;

public record CommunityCommentStatusResponse(long commentId, String status) {

    static CommunityCommentStatusResponse from(CommunityModerationResult result) {
        return new CommunityCommentStatusResponse(result.id(), result.status());
    }
}
