package com.moewadeul.community.infra.post;

import java.time.Instant;

public record CommunityPostRow(
        long postId,
        String title,
        String text,
        long authorId,
        String authorNickname,
        Long spotId,
        String spotName,
        String thumbnailKey,
        long imageCount,
        long likeCount,
        long commentCount,
        long viewCount,
        Instant createdAt,
        Instant updatedAt,
        Boolean likedByMe) {}
