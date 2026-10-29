package com.moewadeul.community.infra.moderation;

import java.time.Instant;

public record AdminCommunityCommentRow(
        long commentId,
        long postId,
        String content,
        long authorId,
        String authorNickname,
        String status,
        long reportCount,
        Instant statusChangedAt) {}
