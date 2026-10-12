package com.moewadeul.memory.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moewadeul.memory.application.MemoryView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 작성자는 회원 ID와 닉네임만 내보낸다. 정확한 좌표(lat, lng)는 작성자 본인에게만 주고, 다른 사람에게는 소수 둘째 자리로 반올림한
// 대략 좌표(approxLat, approxLng)만 준다. 어느 쪽이든 쓰지 않는 필드는 null이 아니라 뺀다.
// likedByMe는 로그인한 사람이 조회할 때만 담고, 비회원이면 필드를 뺀다.
// 연결한 장소가 없거나 지도에 보이지 않으면 spot 필드를, 글이 없으면 content 필드를 뺀다.
// 신고·관리자 조치 상태(status)는 작성자에게만 준다. 다른 사람은 ACTIVE인 기록만 보므로 필드를 뺀다.
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
