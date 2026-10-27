package com.moewadeul.memory.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.memory.api.MemoryDetailResponse.Author;
import com.moewadeul.memory.api.MemoryDetailResponse.LinkedSpot;
import com.moewadeul.memory.application.MemoryFeedItem;
import com.moewadeul.memory.application.MemoryFeedPage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MemoryFeedPageResponse(List<Entry> content, int page, int size, boolean hasNext) {

    static MemoryFeedPageResponse from(MemoryFeedPage page) {
        List<Entry> content = page.content().stream().map(Entry::from).toList();
        return new MemoryFeedPageResponse(content, page.page(), page.size(), page.hasNext());
    }

    public record Entry(
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

        static Entry from(MemoryFeedItem item) {
            return new Entry(
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
}
