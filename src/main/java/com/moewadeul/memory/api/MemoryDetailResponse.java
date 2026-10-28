package com.moewadeul.memory.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.memory.application.MemoryView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MemoryDetailResponse(
        long memoryId,
        Author author,
        @JsonInclude(JsonInclude.Include.NON_NULL) LinkedSpot spot,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double lat,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double lng,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double approxLat,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double approxLng,
        LocalDate visitedDate,
        int nights,
        @JsonInclude(JsonInclude.Include.NON_NULL) String content,
        String visibility,
        @JsonInclude(JsonInclude.Include.NON_NULL) String status,
        List<Image> images,
        Instant createdAt,
        Instant updatedAt,
        long likeCount,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean likedByMe) {

    public record Author(long memberId, String nickname) {}

    public record LinkedSpot(long spotId, String name) {

        static LinkedSpot of(Long spotId, String name) {
            return spotId == null ? null : new LinkedSpot(spotId, name);
        }
    }

    public record Image(long imageId, String url) {}

    static MemoryDetailResponse from(MemoryView view) {
        boolean author = view.viewedByAuthor();
        return new MemoryDetailResponse(
                view.memoryId(),
                new Author(view.authorId(), view.authorNickname()),
                LinkedSpot.of(view.spotId(), view.spotName()),
                author ? view.latitude() : null,
                author ? view.longitude() : null,
                author ? null : view.approximate().latitude(),
                author ? null : view.approximate().longitude(),
                view.visitedDate(),
                view.nights(),
                view.content(),
                view.visibility(),
                author ? view.status() : null,
                view.images().stream()
                        .map(image -> new Image(image.imageId(), image.url()))
                        .toList(),
                view.createdAt(),
                view.updatedAt(),
                view.likeCount(),
                view.likedByMe());
    }
}
