package com.moewadeul.community.infra.comment;

import java.time.Instant;

public record CommunityCommentRow(
        long commentId,
        Long parentId,
        boolean active,
        long authorId,
        String authorNickname,
        Long replyToMemberId,
        String replyToNickname,
        String content,
        Instant createdAt,
        Instant updatedAt) {}
