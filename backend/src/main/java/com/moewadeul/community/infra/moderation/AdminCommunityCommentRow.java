package com.moewadeul.community.infra.moderation;

import com.moewadeul.community.domain.comment.CommunityCommentStatus;
import com.moewadeul.community.domain.post.CommunityPostStatus;
import java.time.Instant;

public record AdminCommunityCommentRow(
        long commentId,
        long postId,
        CommunityPostStatus postStatus,
        String content,
        long authorId,
        String authorNickname,
        CommunityCommentStatus status,
        long reportCount,
        Instant statusChangedAt) {}
