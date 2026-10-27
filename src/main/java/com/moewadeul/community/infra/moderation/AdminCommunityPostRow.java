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
        long spamCount,
        long abuseCount,
        long illegalCampingCount,
        long privacyCount,
        long moneyScamCount,
        long otherCount,
        Instant statusChangedAt) {}
