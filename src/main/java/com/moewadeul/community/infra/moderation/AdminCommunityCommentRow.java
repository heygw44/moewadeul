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
        long spamCount,
        long abuseCount,
        long illegalCampingCount,
        long privacyCount,
        long moneyScamCount,
        long otherCount,
        Instant statusChangedAt) {}
