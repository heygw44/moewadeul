package com.moewadeul.community.api.post;

import com.moewadeul.community.application.post.CommunityPostPage;
import java.util.List;

public record CommunityPostPageResponse(List<CommunityPostItemResponse> content, int page, int size, boolean hasNext) {

    static CommunityPostPageResponse from(CommunityPostPage page) {
        List<CommunityPostItemResponse> content =
                page.content().stream().map(CommunityPostItemResponse::from).toList();
        return new CommunityPostPageResponse(content, page.page(), page.size(), page.hasNext());
    }
}
