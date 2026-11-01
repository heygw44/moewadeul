package com.moewadeul.community.infra.moderation;

import com.moewadeul.community.domain.comment.CommunityCommentStatus;
import java.time.Instant;

public record AdminCommunityCommentRow(
        long commentId,
        long postId,
        String content,
        long authorId,
        String authorNickname,
        CommunityCommentStatus status,
        long reportCount,
        Instant statusChangedAt) {}
