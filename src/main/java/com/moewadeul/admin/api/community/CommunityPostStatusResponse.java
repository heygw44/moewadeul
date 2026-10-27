package com.moewadeul.admin.api.community;

import com.moewadeul.community.application.moderation.CommunityModerationResult;

public record CommunityPostStatusResponse(long postId, String status) {

    static CommunityPostStatusResponse from(CommunityModerationResult result) {
        return new CommunityPostStatusResponse(result.id(), result.status());
    }
}
