package com.moewadeul.memory.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.memory.api.MemoryDetailResponse.Author;
import com.moewadeul.memory.api.MemoryDetailResponse.LinkedSpot;
import com.moewadeul.memory.application.MemoryFeedItem;
import com.moewadeul.memory.application.MemoryFeedPage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 공개 피드의 한 쪽이다. 전체 개수는 없고, 다음 쪽이 있는지만 hasNext로 알린다. */
public record MemoryFeedPageResponse(List<Entry> content, int page, int size, boolean hasNext) {

    static MemoryFeedPageResponse from(MemoryFeedPage page) {
        List<Entry> content = page.content().stream().map(Entry::from).toList();
        return new MemoryFeedPageResponse(content, page.page(), page.size(), page.hasNext());
    }

    // 피드는 누구에게나 같은 모양이라 작성자 본인의 기록이어도 정확한 좌표(lat, lng)는 없고 대략 좌표만 담는다.
    // 연결한 장소가 보이지 않으면 spot을, 글이 없으면 excerpt를, 사진이 없으면 thumbnailUrl을 뺀다. likedByMe는 로그인한 사람이 조회할 때만 담는다.
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
