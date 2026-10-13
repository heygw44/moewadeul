package com.moewadeul.memory.application;

import com.moewadeul.common.web.PatchField;
import com.moewadeul.memory.domain.MemoryDetails;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 기록 수정 요청. 필드마다 요청에 있었는지와 값을 함께 담고, 요청에 없던 필드는 바꾸지 않는다.
 *
 * <p>spotId를 null로 보냈으면 장소 연결을 끊고, content를 null로 보냈으면 글을 지운다. 좌표, 방문일, 박 수, 공개 범위, imageIds는 지울 수
 * 있는 값이 아니라서 null을 거부한다. 위도와 경도는 한 지점을 이루므로 함께 보내야 한다.
 */
public record MemoryReviseCommand(
        PatchField<Long> spotId,
        PatchField<Double> lat,
        PatchField<Double> lng,
        PatchField<LocalDate> visitedDate,
        PatchField<Integer> nights,
        PatchField<String> content,
        PatchField<String> visibility,
        PatchField<List<Long>> imageIds) {

    public MemoryReviseCommand {
        Objects.requireNonNull(spotId, "spotId");
        Objects.requireNonNull(lat, "lat");
        Objects.requireNonNull(lng, "lng");
        Objects.requireNonNull(visitedDate, "visitedDate");
        Objects.requireNonNull(nights, "nights");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(imageIds, "imageIds");
    }

    /**
     * 호출하면 요청에 있는 값으로 current를 덮어쓴 작성 요청을 만든다. 사진은 따로 바꾸므로 결과에 넣지 않는다.
     *
     * <p>지울 수 없는 필드에 null을 보냈거나, 위도와 경도 중 하나만 보냈으면 그 필드의 INVALID_INPUT으로 거부한다.
     */
    MemoryWriteCommand mergeInto(MemoryDetails current) {
        requireNotCleared(lat, "lat", "위도");
        requireNotCleared(lng, "lng", "경도");
        requireNotCleared(visitedDate, "visitedDate", "방문일");
        requireNotCleared(nights, "nights", "박 수");
        requireNotCleared(visibility, "visibility", "공개 범위");
        if (lat.present() != lng.present()) {
            throw MemoryWriteCommand.invalid(lat.present() ? "lng" : "lat", "위도와 경도는 함께 보내 주세요.");
        }
        return new MemoryWriteCommand(
                pick(spotId, current.spotId()),
                pick(lat, current.latitude()),
                pick(lng, current.longitude()),
                pick(visitedDate, current.visitedDate()),
                pick(nights, current.nights()),
                pick(content, current.content()),
                pick(visibility, current.visibility().name()),
                List.of());
    }

    /** 요청이 장소를 currentSpotId가 아닌 다른 장소로 바꾸면 true다. 연결을 끊거나 같은 장소를 다시 보내면 false다. */
    boolean linksNewSpot(Long currentSpotId) {
        return spotId.present() && spotId.value() != null && !spotId.value().equals(currentSpotId);
    }

    private static void requireNotCleared(PatchField<?> field, String name, String label) {
        if (field.present() && field.value() == null) {
            throw MemoryWriteCommand.invalid(name, label + "은(는) 비울 수 없어요.");
        }
    }

    private static <T> T pick(PatchField<T> field, T current) {
        return field.present() ? field.value() : current;
    }
}
