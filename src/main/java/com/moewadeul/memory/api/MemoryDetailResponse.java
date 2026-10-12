package com.moewadeul.memory.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.memory.application.MemoryView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 작성자는 회원 ID와 닉네임만 내보낸다. 정확한 좌표는 작성자 본인에게만 주고, 다른 사람에게는 null이 아니라 필드를 뺀다.
// 연결한 장소가 없거나 지도에 보이지 않으면 spot 필드를, 글이 없으면 content 필드를 뺀다.
public record MemoryDetailResponse(
        long memoryId,
        Author author,
        @JsonInclude(JsonInclude.Include.NON_NULL) LinkedSpot spot,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double lat,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double lng,
        LocalDate visitedDate,
        int nights,
        @JsonInclude(JsonInclude.Include.NON_NULL) String content,
        String visibility,
        List<Image> images,
        Instant createdAt,
        Instant updatedAt) {

    public record Author(long memberId, String nickname) {}

    public record LinkedSpot(long spotId, String name) {

        static LinkedSpot of(Long spotId, String name) {
            return spotId == null ? null : new LinkedSpot(spotId, name);
        }
    }

    /** 기록 안 순서대로 담는다. 사진이 없으면 빈 배열이다. url은 응답마다 새로 서명한 조회 URL이다. */
    public record Image(long imageId, String url) {}

    static MemoryDetailResponse from(MemoryView view) {
        boolean author = view.viewedByAuthor();
        return new MemoryDetailResponse(
                view.memoryId(),
                new Author(view.authorId(), view.authorNickname()),
                LinkedSpot.of(view.spotId(), view.spotName()),
                author ? view.latitude() : null,
                author ? view.longitude() : null,
                view.visitedDate(),
                view.nights(),
                view.content(),
                view.visibility(),
                view.images().stream()
                        .map(image -> new Image(image.imageId(), image.url()))
                        .toList(),
                view.createdAt(),
                view.updatedAt());
    }
}
