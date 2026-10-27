package com.moewadeul.community.application.comment;

import com.moewadeul.community.infra.comment.CommunityCommentRow;
import java.time.Instant;
import java.util.List;

public record CommunityCommentItem(
        long commentId,
        boolean deleted,
        Long authorId,
        String authorNickname,
        Long replyToMemberId,
        String replyToNickname,
        String content,
        Instant createdAt,
        Instant updatedAt,
        List<CommunityCommentItem> replies) {

    static CommunityCommentItem from(CommunityCommentRow row, List<CommunityCommentItem> replies) {
        if (!row.active()) {
            return new CommunityCommentItem(row.commentId(), true, null, null, null, null, null, null, null, replies);
        }
        return new CommunityCommentItem(
                row.commentId(),
                false,
                row.authorId(),
                row.authorNickname(),
                row.replyToMemberId(),
                row.replyToNickname(),
                row.content(),
                row.createdAt(),
                row.updatedAt(),
                replies);
    }
}
