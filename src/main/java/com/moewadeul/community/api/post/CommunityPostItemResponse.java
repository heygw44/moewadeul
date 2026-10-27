package com.moewadeul.community.api.post;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.community.application.post.CommunityPostItem;
import java.time.Instant;

public record CommunityPostItemResponse(
        long postId,
        String title,
        String excerpt,
        Author author,
        @JsonInclude(JsonInclude.Include.NON_NULL) LinkedSpot spot,
        @JsonInclude(JsonInclude.Include.NON_NULL) String thumbnailUrl,
        long imageCount,
        long likeCount,
        long commentCount,
        long viewCount,
        Instant createdAt) {

    static CommunityPostItemResponse from(CommunityPostItem item) {
        return new CommunityPostItemResponse(
                item.postId(),
                item.title(),
                item.text(),
                new Author(item.authorId(), item.authorNickname()),
                LinkedSpot.of(item),
                item.thumbnailUrl(),
                item.imageCount(),
                item.likeCount(),
                item.commentCount(),
                item.viewCount(),
                item.createdAt());
    }

    public record Author(long memberId, String nickname) {}

    public record LinkedSpot(long spotId, String name) {

        static LinkedSpot of(CommunityPostItem item) {
            return item.spotId() == null ? null : new LinkedSpot(item.spotId(), item.spotName());
        }
    }
}
