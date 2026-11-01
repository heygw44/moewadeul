package com.moewadeul.community.api.like;

import com.moewadeul.community.application.like.CommunityPostLikeResult;

public record CommunityPostLikeResponse(boolean liked, long likeCount) {

    static CommunityPostLikeResponse from(CommunityPostLikeResult result) {
        return new CommunityPostLikeResponse(result.liked(), result.likeCount());
    }
}
