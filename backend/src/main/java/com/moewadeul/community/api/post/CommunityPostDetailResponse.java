package com.moewadeul.community.api.post;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.community.api.post.CommunityPostItemResponse.Author;
import com.moewadeul.community.api.post.CommunityPostItemResponse.LinkedSpot;
import com.moewadeul.community.application.post.CommunityPostItem;
import java.time.Instant;
import java.util.List;

public record CommunityPostDetailResponse(
        long postId,
        String title,
        String content,
        List<Image> images,
        Author author,
        @JsonInclude(JsonInclude.Include.NON_NULL) LinkedSpot spot,
        long likeCount,
        long commentCount,
        long viewCount,
        Instant createdAt,
        Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean likedByMe) {

    public record Image(long imageId, String url) {}

    static CommunityPostDetailResponse from(CommunityPostItem item) {
        return new CommunityPostDetailResponse(
                item.postId(),
                item.title(),
                item.text(),
                item.images().stream()
                        .map(image -> new Image(image.imageId(), image.url()))
                        .toList(),
                new Author(item.authorId(), item.authorNickname()),
                LinkedSpot.of(item),
                item.likeCount(),
                item.commentCount(),
                item.viewCount(),
                item.createdAt(),
                item.updatedAt(),
                item.likedByMe());
    }
}
