package com.moewadeul.community.api.comment;

import com.moewadeul.community.application.comment.CommunityCommentPage;
import java.util.List;

public record CommunityCommentPageResponse(
        List<CommunityCommentItemResponse> content, int page, int size, boolean hasNext) {

    static CommunityCommentPageResponse from(CommunityCommentPage page) {
        List<CommunityCommentItemResponse> content =
                page.content().stream().map(CommunityCommentItemResponse::from).toList();
        return new CommunityCommentPageResponse(content, page.page(), page.size(), page.hasNext());
    }
}
