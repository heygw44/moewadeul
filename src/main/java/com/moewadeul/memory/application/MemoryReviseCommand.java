package com.moewadeul.memory.application;

import com.moewadeul.common.patch.PatchField;
import com.moewadeul.memory.domain.MemoryDetails;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

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
