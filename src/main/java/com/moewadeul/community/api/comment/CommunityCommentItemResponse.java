package com.moewadeul.community.api.comment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.community.application.comment.CommunityCommentItem;
import java.time.Instant;
import java.util.List;

public record CommunityCommentItemResponse(
        long commentId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Author author,
        @JsonInclude(JsonInclude.Include.NON_NULL) Author replyTo,
        @JsonInclude(JsonInclude.Include.NON_NULL) String content,
        boolean deleted,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant updatedAt,
        List<CommunityCommentItemResponse> replies) {

    static CommunityCommentItemResponse from(CommunityCommentItem item) {
        return new CommunityCommentItemResponse(
                item.commentId(),
                item.deleted() ? null : new Author(item.authorId(), item.authorNickname()),
                item.replyToMemberId() == null ? null : new Author(item.replyToMemberId(), item.replyToNickname()),
                item.content(),
                item.deleted(),
                item.createdAt(),
                item.updatedAt(),
                item.replies().stream().map(CommunityCommentItemResponse::from).toList());
    }

    public record Author(long memberId, String nickname) {}
}
