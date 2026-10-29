package com.moewadeul.community.infra.moderation;

import com.moewadeul.community.domain.post.CommunityPostStatus;
import java.time.Instant;

public record AdminCommunityPostRow(
        long postId,
        String title,
        String content,
        long authorId,
        String authorNickname,
        CommunityPostStatus status,
        long reportCount,
        Instant statusChangedAt) {}
