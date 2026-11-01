package com.moewadeul.memory.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.memory.api.MemoryDetailResponse.Author;
import com.moewadeul.memory.api.MemoryDetailResponse.LinkedSpot;
import com.moewadeul.memory.application.MemoryFeedItem;
import java.time.Instant;
import java.time.LocalDate;

public record MemoryFeedItemResponse(
        long memoryId,
        Author author,
        @JsonInclude(JsonInclude.Include.NON_NULL) LinkedSpot spot,
        double approxLat,
        double approxLng,
        LocalDate visitedDate,
        int nights,
        @JsonInclude(JsonInclude.Include.NON_NULL) String excerpt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String thumbnailUrl,
        long imageCount,
        long likeCount,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean likedByMe,
        Instant createdAt) {

    static MemoryFeedItemResponse from(MemoryFeedItem item) {
        return new MemoryFeedItemResponse(
                item.memoryId(),
                new Author(item.authorId(), item.authorNickname()),
                LinkedSpot.of(item.spotId(), item.spotName()),
                item.approximate().latitude(),
                item.approximate().longitude(),
                item.visitedDate(),
                item.nights(),
                item.excerpt(),
                item.thumbnailUrl(),
                item.imageCount(),
                item.likeCount(),
                item.likedByMe(),
                item.createdAt());
    }
}
