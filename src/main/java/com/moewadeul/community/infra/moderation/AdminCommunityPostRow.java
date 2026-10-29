package com.moewadeul.community.infra.moderation;

import java.time.Instant;

public record AdminCommunityPostRow(
        long postId,
        String title,
        String content,
        long authorId,
        String authorNickname,
        String status,
        long reportCount,
        Instant statusChangedAt) {}
