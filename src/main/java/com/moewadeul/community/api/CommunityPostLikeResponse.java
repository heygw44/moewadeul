package com.moewadeul.community.api;

import com.moewadeul.community.application.CommunityPostLikeResult;

public record CommunityPostLikeResponse(boolean liked, long likeCount) {

    static CommunityPostLikeResponse from(CommunityPostLikeResult result) {
        return new CommunityPostLikeResponse(result.liked(), result.likeCount());
    }
}
