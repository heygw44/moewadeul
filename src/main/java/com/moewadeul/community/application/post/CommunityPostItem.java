package com.moewadeul.community.application.post;

import com.moewadeul.community.infra.post.CommunityPostRow;
import java.time.Instant;
import java.util.List;

public record CommunityPostItem(
        long postId,
        String title,
        String text,
        long authorId,
        String authorNickname,
        Long spotId,
        String spotName,
        String thumbnailUrl,
        long imageCount,
        List<CommunityPostImage> images,
        long likeCount,
        long commentCount,
        long viewCount,
        Instant createdAt,
        Instant updatedAt,
        Boolean likedByMe) {

    static CommunityPostItem from(CommunityPostRow row, String thumbnailUrl, List<CommunityPostImage> images) {
        return new CommunityPostItem(
                row.postId(),
                row.title(),
                row.text(),
                row.authorId(),
                row.authorNickname(),
                row.spotId(),
                row.spotName(),
                thumbnailUrl,
                row.imageCount(),
                images,
                row.likeCount(),
                row.commentCount(),
                row.viewCount(),
                row.createdAt(),
                row.updatedAt(),
                row.likedByMe());
    }

    CommunityPostItem withMoreViews(long views) {
        return new CommunityPostItem(
                postId,
                title,
                text,
                authorId,
                authorNickname,
                spotId,
                spotName,
                thumbnailUrl,
                imageCount,
                images,
                likeCount,
                commentCount,
                viewCount + views,
                createdAt,
                updatedAt,
                likedByMe);
    }
}
